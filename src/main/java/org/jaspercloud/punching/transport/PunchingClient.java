package org.jaspercloud.punching.transport;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.util.ReferenceCountUtil;
import org.jaspercloud.punching.proto.PunchingProtos;
import org.springframework.beans.factory.InitializingBean;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

public class PunchingClient implements InitializingBean {

    private String serverHost;
    private int serverPort;
    private int localPort;
    private Channel channel;

    private Map<String, CompletableFuture<PunchingProtos.PunchingMessage>> futureMap = new ConcurrentHashMap<>();

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
        InetSocketAddress remote = new InetSocketAddress(serverHost, serverPort);
        InetSocketAddress local = new InetSocketAddress("0.0.0.0", localPort);
        NioEventLoopGroup group = new NioEventLoopGroup();
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group)
                .channel(NioDatagramChannel.class)
                .handler(new ChannelInitializer<DatagramChannel>() {
                    @Override
                    protected void initChannel(DatagramChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addFirst(new ChannelDuplexHandler() {

                            private ChannelPromise promise;

                            @Override
                            public void bind(ChannelHandlerContext ctx, SocketAddress localAddress, ChannelPromise promise) throws Exception {
                                this.promise = promise;
                                ChannelPromise bindPromise = ctx.newPromise();
                                bindPromise.addListener(new ChannelFutureListener() {
                                    @Override
                                    public void operationComplete(ChannelFuture future) throws Exception {
                                        Channel channel = ctx.channel();
                                        channel.eventLoop().scheduleAtFixedRate(() -> {
                                            PunchingProtos.PunchingMessage message = PunchingProtos.PunchingMessage.newBuilder()
                                                    .setType(PunchingProtos.MsgType.ReqRegisterType)
                                                    .setReqId(UUID.randomUUID().toString())
                                                    .build();
                                            ByteBuf byteBuf = ProtosUtil.toBuffer(channel.alloc(), message);
                                            DatagramPacket packet = new DatagramPacket(byteBuf, remote);
                                            channel.writeAndFlush(packet);
                                        }, 0, 5, TimeUnit.SECONDS);
                                    }
                                });
                                super.bind(ctx, localAddress, bindPromise);
                            }

                            @Override
                            public void channelActive(ChannelHandlerContext ctx) throws Exception {
                            }

                            @Override
                            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                                boolean release = true;
                                try {
                                    DatagramPacket packet = (DatagramPacket) msg;
                                    ByteBuf byteBuf = packet.content();
                                    byteBuf.markReaderIndex();
                                    PunchingProtos.PunchingMessage request = ProtosUtil.toProto(byteBuf);
                                    switch (request.getType().getNumber()) {
                                        case PunchingProtos.MsgType.RespRegisterType_VALUE: {
                                            PunchingProtos.ConnectionData connectionData = PunchingProtos.ConnectionData.parseFrom(request.getData());
                                            AttributeKeyUtil.connectionData(ctx.channel()).set(connectionData);
                                            if (!promise.isDone()) {
                                                InetSocketAddress localAddress = (InetSocketAddress) ctx.channel().localAddress();
                                                System.out.println(String.format("active: %s:%d -> %s:%d",
                                                        localAddress.getHostName(), localAddress.getPort(),
                                                        connectionData.getHost(), connectionData.getPort()));
                                                promise.setSuccess();
                                                ctx.fireChannelActive();
                                            }
                                            break;
                                        }
                                        default: {
                                            release = false;
                                            byteBuf.resetReaderIndex();
                                            super.channelRead(ctx, msg);
                                        }
                                    }
                                } finally {
                                    if (release) {
                                        ReferenceCountUtil.release(msg);
                                    }
                                }
                            }
                        });
                        pipeline.addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelActive(ChannelHandlerContext ctx) throws Exception {
                                super.channelActive(ctx);
                            }

                            @Override
                            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                                DatagramPacket packet = (DatagramPacket) msg;
                                PunchingProtos.PunchingMessage request = ProtosUtil.toProto(packet.content());
                                switch (request.getType().getNumber()) {
                                    case PunchingProtos.MsgType.PingType_VALUE: {
                                        PunchingProtos.PunchingMessage message = PunchingProtos.PunchingMessage.newBuilder()
                                                .setType(PunchingProtos.MsgType.PongType)
                                                .setReqId(request.getReqId())
                                                .build();
                                        ByteBuf byteBuf = ProtosUtil.toBuffer(ctx.alloc(), message);
                                        DatagramPacket data = new DatagramPacket(byteBuf, packet.sender());
                                        ctx.writeAndFlush(data);
                                        break;
                                    }
                                    case PunchingProtos.MsgType.PongType_VALUE: {
                                        CompletableFuture<PunchingProtos.PunchingMessage> future = futureMap.remove(request.getReqId());
                                        if (null != future) {
                                            future.complete(request);
                                        }
                                        break;
                                    }
                                    case PunchingProtos.MsgType.PunchingType_VALUE: {
                                        PunchingProtos.PunchingData punchingData = PunchingProtos.PunchingData.parseFrom(request.getData());
                                        PunchingProtos.PunchingMessage message = PunchingProtos.PunchingMessage.newBuilder()
                                                .setType(PunchingProtos.MsgType.PongType)
                                                .setReqId(request.getReqId())
                                                .build();
                                        ByteBuf byteBuf = ProtosUtil.toBuffer(ctx.alloc(), message);
                                        DatagramPacket data = new DatagramPacket(byteBuf, new InetSocketAddress(punchingData.getPingHost(), punchingData.getPingPort()));
                                        ctx.writeAndFlush(data);
                                        break;
                                    }
                                }
                            }
                        });
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

//    public void punching(String host, int port, long timeout) throws ExecutionException, InterruptedException, TimeoutException {
//        String id = channel.id().asLongText();
//        CompletableFuture future = new CompletableFuture();
//        futureMap.put(id, future);
//        ScheduledFuture<?> pingFuture = channel.eventLoop().scheduleAtFixedRate(() -> {
//            PunchingProtos.PunchingMessage message = PunchingProtos.PunchingMessage.newBuilder()
//                    .setType(PunchingProtos.MsgType.PingType)
//                    .setReqId(id)
//                    .build();
//            ByteBuf byteBuf = ProtosUtil.toBuffer(channel.alloc(), message);
//            DatagramPacket packet = new DatagramPacket(byteBuf, new InetSocketAddress(host, port));
//            System.out.println(String.format("Ping: %s:%d", host, port));
//            channel.writeAndFlush(packet);
//        }, 0, 1000, TimeUnit.MILLISECONDS);
//        ScheduledFuture<?> punchingFuture = channel.eventLoop().scheduleAtFixedRate(() -> {
//            PunchingProtos.ConnectionData connectionData = (PunchingProtos.ConnectionData) AttributeKeyUtil.connectionData(channel).get();
//            PunchingProtos.PunchingData punchingData = PunchingProtos.PunchingData.newBuilder()
//                    .setPingHost(connectionData.getHost())
//                    .setPingPort(connectionData.getPort())
//                    .setPongHost(host)
//                    .setPongPort(port)
//                    .build();
//            PunchingProtos.PunchingMessage message = PunchingProtos.PunchingMessage.newBuilder()
//                    .setType(PunchingProtos.MsgType.PunchingType)
//                    .setReqId(id)
//                    .setData(punchingData.toByteString())
//                    .build();
//            ByteBuf byteBuf = ProtosUtil.toBuffer(channel.alloc(), message);
//            DatagramPacket packet = new DatagramPacket(byteBuf, new InetSocketAddress(serverHost, serverPort));
//            System.out.println(String.format("Punching: %s:%d", serverHost, serverPort));
//            channel.writeAndFlush(packet);
//        }, 0, 1000, TimeUnit.MILLISECONDS);
//        try {
//            future.get(timeout, TimeUnit.MILLISECONDS);
//        } finally {
//            pingFuture.cancel(true);
//            punchingFuture.cancel(true);
//        }
//    }

    public void punching(String host, int port, long timeout) throws ExecutionException, InterruptedException, TimeoutException {
        String id = channel.id().asLongText();
        CompletableFuture future = new CompletableFuture();
        futureMap.put(id, future);
        PunchingProtos.ConnectionData connectionData = (PunchingProtos.ConnectionData) AttributeKeyUtil.connectionData(channel).get();
        PunchingProtos.PunchingData punchingData = PunchingProtos.PunchingData.newBuilder()
                .setPingHost(connectionData.getHost())
                .setPingPort(connectionData.getPort())
                .setPongHost(host)
                .setPongPort(port)
                .build();
        PunchingProtos.PunchingMessage message = PunchingProtos.PunchingMessage.newBuilder()
                .setType(PunchingProtos.MsgType.PunchingType)
                .setReqId(id)
                .setData(punchingData.toByteString())
                .build();
        ByteBuf byteBuf = ProtosUtil.toBuffer(channel.alloc(), message);
        DatagramPacket packet = new DatagramPacket(byteBuf, new InetSocketAddress(serverHost, serverPort));
        System.out.println(String.format("Punching: %s:%d", serverHost, serverPort));
        channel.writeAndFlush(packet);
        try {
            future.get(timeout, TimeUnit.MILLISECONDS);
        } finally {
            futureMap.remove(id);
        }
    }
}
