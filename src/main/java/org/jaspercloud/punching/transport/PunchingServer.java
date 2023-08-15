package org.jaspercloud.punching.transport;

import com.google.protobuf.InvalidProtocolBufferException;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import org.jaspercloud.punching.exception.ParseException;
import org.jaspercloud.punching.proto.PunchingProtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

import java.net.InetSocketAddress;
import java.util.UUID;

public class PunchingServer implements InitializingBean {

    private Logger logger = LoggerFactory.getLogger(getClass());

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
                        pipeline.addLast("decoder", new Decoder());
                        pipeline.addLast("encoder", new Encoder());
                        pipeline.addLast(new SimpleChannelInboundHandler<Envelope<PunchingProtos.PunchingMessage>>() {
                            @Override
                            protected void channelRead0(ChannelHandlerContext ctx, Envelope<PunchingProtos.PunchingMessage> msg) {
                                PunchingProtos.PunchingMessage request = msg.message();
                                switch (request.getType().getNumber()) {
                                    case PunchingProtos.MsgType.ReqRegisterType_VALUE: {
                                        processRegister(ctx, msg);
                                        break;
                                    }
                                    case PunchingProtos.MsgType.ReqRelayPunchingType_VALUE: {
                                        processRelayPunching(ctx, msg);
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

    private void processRegister(ChannelHandlerContext ctx, Envelope<PunchingProtos.PunchingMessage> envelope) {
        InetSocketAddress sender = envelope.sender();
        String host = sender.getHostString();
        int port = sender.getPort();
        logger.debug("register: {}:{}", host, port);
        PunchingProtos.PunchingMessage message = PunchingProtos.PunchingMessage.newBuilder()
                .setType(PunchingProtos.MsgType.RespRegisterType)
                .setReqId(UUID.randomUUID().toString())
                .setData(PunchingProtos.ConnectionData.newBuilder()
                        .setHost(host)
                        .setPort(port)
                        .build().toByteString())
                .build();
        Envelope data = Envelope.builder()
                .recipient(sender)
                .message(message)
                .build();
        ctx.writeAndFlush(data);
    }

    private void processRelayPunching(ChannelHandlerContext ctx, Envelope<PunchingProtos.PunchingMessage> envelope) {
        try {
            PunchingProtos.PunchingMessage message = envelope.message();
            PunchingProtos.PunchingData punchingData = PunchingProtos.PunchingData.parseFrom(message.getData());
            logger.debug("relayPunching: {}:{} -> {}:{}",
                    punchingData.getPingHost(), punchingData.getPingPort(),
                    punchingData.getPongHost(), punchingData.getPongPort());
            Envelope data = Envelope.builder()
                    .recipient(new InetSocketAddress(punchingData.getPongHost(), punchingData.getPongPort()))
                    .message(message)
                    .build();
            ctx.writeAndFlush(data);
        } catch (InvalidProtocolBufferException e) {
            throw new ParseException(e.getMessage(), e);
        }
    }
}
