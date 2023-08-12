package org.jaspercloud.punching.transport;

import com.google.protobuf.InvalidProtocolBufferException;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import org.jaspercloud.punching.exception.ParseException;
import org.jaspercloud.punching.proto.PunchingProtos;
import org.springframework.beans.factory.InitializingBean;

import java.net.InetSocketAddress;
import java.util.UUID;

public class PunchingServer implements InitializingBean {

    private int port;

    public PunchingServer(int port) {
        this.port = port;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        NioEventLoopGroup group = new NioEventLoopGroup();
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group)
                .channel(NioDatagramChannel.class)
                .handler(new ChannelInitializer<DatagramChannel>() {
                    @Override
                    protected void initChannel(DatagramChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(new SimpleChannelInboundHandler<DatagramPacket>() {
                            @Override
                            protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) {
                                PunchingProtos.PunchingMessage request = ProtosUtil.toProto(msg.content());
                                switch (request.getType().getNumber()) {
                                    case PunchingProtos.MsgType.ReqRegisterType_VALUE: {
                                        processRegister(ctx, msg.sender(), request);
                                        break;
                                    }
                                    case PunchingProtos.MsgType.PunchingType_VALUE: {
                                        processPunching(ctx, msg.sender(), request);
                                        break;
                                    }
                                }
                            }
                        });
                    }
                });
        Channel channel = bootstrap.bind(port).sync().channel();
        channel.closeFuture().addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                group.shutdownGracefully();
            }
        });
    }

    private void processRegister(ChannelHandlerContext ctx, InetSocketAddress sender, PunchingProtos.PunchingMessage request) {
        String host = sender.getHostName();
        int port = sender.getPort();
        System.out.println(String.format("register: %s:%d", host, port));
        PunchingProtos.PunchingMessage message = PunchingProtos.PunchingMessage.newBuilder()
                .setType(PunchingProtos.MsgType.RespRegisterType)
                .setReqId(UUID.randomUUID().toString())
                .setData(PunchingProtos.ConnectionData.newBuilder()
                        .setHost(host)
                        .setPort(port)
                        .build().toByteString())
                .build();
        ByteBuf byteBuf = ProtosUtil.toBuffer(ctx.alloc(), message);
        DatagramPacket packet = new DatagramPacket(byteBuf, sender);
        ctx.writeAndFlush(packet);
    }

    private void processPunching(ChannelHandlerContext ctx, InetSocketAddress sender, PunchingProtos.PunchingMessage request) {
        try {
            PunchingProtos.PunchingData punchingData = PunchingProtos.PunchingData.parseFrom(request.getData());
            System.out.println(String.format("%s:%d -> %s:%d",
                    punchingData.getPingHost(), punchingData.getPingPort(),
                    punchingData.getPongHost(), punchingData.getPongPort()));
            ByteBuf byteBuf = ProtosUtil.toBuffer(ctx.alloc(), request);
            DatagramPacket packet = new DatagramPacket(byteBuf, new InetSocketAddress(punchingData.getPongHost(), punchingData.getPongPort()));
            ctx.writeAndFlush(packet);
        } catch (InvalidProtocolBufferException e) {
            throw new ParseException(e.getMessage(), e);
        }
    }
}
