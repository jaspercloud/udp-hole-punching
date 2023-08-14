package org.jaspercloud.punching.transport;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.util.concurrent.ScheduledFuture;
import org.jaspercloud.punching.domain.NodeData;
import org.jaspercloud.punching.domain.RelayPunchingData;
import org.jaspercloud.punching.proto.PunchingProtos;
import org.springframework.beans.factory.InitializingBean;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

public class PunchingClient implements InitializingBean {

    private String serverHost;
    private int serverPort;
    private int localPort;
    private Channel channel;
    private NodeManager nodeManager = new NodeManager();

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
                        pipeline.addLast("handler", new ClientHandler(nodeManager));
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
        NodeData nodeData = nodeManager.addNode(channel, host, port);
        ScheduledFuture<?> relayPunchingSchedule = channel.eventLoop().scheduleAtFixedRate(() -> {
            PunchingProtos.ConnectionData connectionData = (PunchingProtos.ConnectionData) AttributeKeyUtil.connectionData(channel).get();
            PunchingProtos.PunchingData punchingData = PunchingProtos.PunchingData.newBuilder()
                    .setPingHost(connectionData.getHost())
                    .setPingPort(connectionData.getPort())
                    .setPongHost(host)
                    .setPongPort(port)
                    .build();
            PunchingProtos.PunchingMessage message = PunchingProtos.PunchingMessage.newBuilder()
                    .setType(PunchingProtos.MsgType.ReqRelayPunchingType)
                    .setReqId(UUID.randomUUID().toString())
                    .setData(punchingData.toByteString())
                    .build();
            ByteBuf byteBuf = ProtosUtil.toBuffer(channel.alloc(), message);
            DatagramPacket packet = new DatagramPacket(byteBuf, new InetSocketAddress(serverHost, serverPort));
            System.out.println(String.format("relayPunching: %s:%d", serverHost, serverPort));
            channel.writeAndFlush(packet);
        }, 0, 100, TimeUnit.MILLISECONDS);
        try {
            nodeData.getFuture().get(timeout, TimeUnit.MILLISECONDS);
        } finally {
            relayPunchingSchedule.cancel(true);
        }
    }
}
