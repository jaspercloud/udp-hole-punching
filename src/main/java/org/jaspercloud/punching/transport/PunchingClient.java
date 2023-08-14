package org.jaspercloud.punching.transport;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.util.concurrent.ScheduledFuture;
import org.jaspercloud.punching.domain.RelayPunchingData;
import org.jaspercloud.punching.proto.PunchingProtos;
import org.springframework.beans.factory.InitializingBean;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public class PunchingClient implements InitializingBean {

    private String serverHost;
    private int serverPort;
    private int localPort;
    private Channel channel;

    private Map<String, CompletableFuture> futureMap = new ConcurrentHashMap<>();

    public PunchingClient(String host, int port) {
        this(host, port, 0);
    }

    public PunchingClient(String serverHost, int serverPort, int localPort) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.localPort = localPort;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        InetSocketAddress serverAddress = new InetSocketAddress(serverHost, serverPort);
        InetSocketAddress local = new InetSocketAddress("0.0.0.0", localPort);
        NioEventLoopGroup group = new NioEventLoopGroup();
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group)
                .channel(NioDatagramChannel.class)
                .handler(new ChannelInitializer<DatagramChannel>() {
                    @Override
                    protected void initChannel(DatagramChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addFirst("register", new RegisterHandler(serverAddress));
                        pipeline.addLast("handler", new ClientHandler(futureMap));
                    }
                });
        channel = bootstrap.bind(local).sync().channel();
        channel.closeFuture().addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                group.shutdownGracefully();
            }
        });
    }

    public void punching(String host, int port, long timeout) throws ExecutionException, InterruptedException, TimeoutException {
        AtomicReference<Integer> portRef = new AtomicReference<>(port);
        String id = channel.id().asLongText();
        CompletableFuture<RelayPunchingData> future = new CompletableFuture<>();
        future.thenAccept(data -> {
            portRef.set(data.getPort());
        });
        futureMap.put(id, future);
        ScheduledFuture<?> pingFuture = channel.eventLoop().scheduleAtFixedRate(() -> {
            PunchingProtos.PunchingMessage message = PunchingProtos.PunchingMessage.newBuilder()
                    .setType(PunchingProtos.MsgType.PingType)
                    .setReqId(id)
                    .build();
            ByteBuf byteBuf = ProtosUtil.toBuffer(channel.alloc(), message);
            DatagramPacket packet = new DatagramPacket(byteBuf, new InetSocketAddress(host, portRef.get()));
            System.out.println(String.format("ping: %s:%d", packet.recipient().getHostString(), packet.recipient().getPort()));
            channel.writeAndFlush(packet);
        }, 0, 1000, TimeUnit.MILLISECONDS);
        ScheduledFuture<?> relayPunchingSchedule = channel.eventLoop().scheduleAtFixedRate(() -> {
            PunchingProtos.ConnectionData connectionData = (PunchingProtos.ConnectionData) AttributeKeyUtil.connectionData(channel).get();
            PunchingProtos.PunchingData punchingData = PunchingProtos.PunchingData.newBuilder()
                    .setPingHost(connectionData.getHost())
                    .setPingPort(connectionData.getPort())
                    .setPongHost(host)
                    .setPongPort(port)
                    .build();
            PunchingProtos.PunchingMessage message = PunchingProtos.PunchingMessage.newBuilder()
                    .setType(PunchingProtos.MsgType.RelayPunchingType)
                    .setReqId(id)
                    .setData(punchingData.toByteString())
                    .build();
            ByteBuf byteBuf = ProtosUtil.toBuffer(channel.alloc(), message);
            DatagramPacket packet = new DatagramPacket(byteBuf, new InetSocketAddress(serverHost, serverPort));
            System.out.println(String.format("relayPunching: %s:%d", serverHost, serverPort));
            channel.writeAndFlush(packet);
        }, 0, 1000, TimeUnit.MILLISECONDS);
        try {
            future.get(timeout, TimeUnit.MILLISECONDS);
        } finally {
            futureMap.remove(id);
            pingFuture.cancel(true);
            relayPunchingSchedule.cancel(true);
        }
    }
}
