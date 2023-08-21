package org.jaspercloud.punching.transport;

import io.netty.channel.*;
import org.apache.commons.lang3.StringUtils;
import org.jaspercloud.punching.proto.PunchingProtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class TunnelChannel extends BusChannel {

    private static Logger logger = LoggerFactory.getLogger(TunnelChannel.class);

    private static Map<String, TunnelChannel> TunnelMap = new ConcurrentHashMap<>();
    private static Map<String, StreamChannel> StreamMap = new ConcurrentHashMap<>();

    private PunchingProtos.ConnectionData connectionData;
    private SocketAddress remoteAddress;

    public PunchingProtos.ConnectionData getConnectionData() {
        return connectionData;
    }

    public void addStreamChannel(StreamChannel channel) {
        channel.closeFuture().addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                Channel channel = future.channel();
                StreamMap.remove(channel.id().asLongText());
            }
        });
        StreamMap.put(channel.id().asLongText(), channel);
    }

    @Override
    public SocketAddress localAddress() {
        return parent().localAddress();
    }

    @Override
    public SocketAddress remoteAddress() {
        return remoteAddress;
    }

    private TunnelChannel(Channel channel) {
        super(channel);
    }

    public static SimpleChannelInboundHandler<Envelope<PunchingProtos.PunchingMessage>> createHandler() {
        SimpleChannelInboundHandler<Envelope<PunchingProtos.PunchingMessage>> handler = new SimpleChannelInboundHandler<Envelope<PunchingProtos.PunchingMessage>>() {
            @Override
            protected void channelRead0(ChannelHandlerContext ctx, Envelope<PunchingProtos.PunchingMessage> msg) throws Exception {
                PunchingProtos.PunchingMessage request = msg.message();
                TunnelChannel tunnelChannel = TunnelMap.get(request.getChannelId());
                if (null == tunnelChannel) {
                    tunnelChannel = TunnelChannel.create(ctx.channel());
                }
                tunnelChannel.receive(msg);
            }
        };
        return handler;
    }

    public static TunnelChannel create(Channel parent) throws InterruptedException {
        TunnelChannel tunnelChannel = new TunnelChannel(parent);
        TunnelMap.put(tunnelChannel.id().asLongText(), tunnelChannel);
        tunnelChannel.closeFuture().addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                TunnelMap.remove(tunnelChannel.id().asLongText());
            }
        });
        tunnelChannel.pipeline().addLast("init", new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ch.pipeline().addLast("registerReq", new RegisterReqHandler(parent, tunnelChannel));
                ch.pipeline().addLast("tunnel", new TunnelHandler(tunnelChannel));
            }
        });
        parent.eventLoop().register(tunnelChannel).sync();
        return tunnelChannel;
    }

    @Override
    public ChannelFuture writeAndFlush(Object msg) {
        return parent().writeAndFlush(msg);
    }

    private static class RegisterReqHandler extends ChannelDuplexHandler {

        private Channel parent;
        private TunnelChannel tunnelChannel;

        public RegisterReqHandler(Channel parent, TunnelChannel tunnelChannel) {
            this.parent = parent;
            this.tunnelChannel = tunnelChannel;
        }

        @Override
        public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) throws Exception {
            Channel channel = ctx.channel();
            channel.pipeline().addAfter("registerReq", "registerResp", new RegisterRespHandler(tunnelChannel, promise));
            AtomicReference<Integer> delayRef = new AtomicReference<>(100);
            new Runnable() {
                @Override
                public void run() {
                    try {
                        sendRegister(ctx, (InetSocketAddress) remoteAddress);
                    } finally {
                        if (channel.isActive()) {
                            channel.eventLoop().schedule(this, delayRef.get(), TimeUnit.MILLISECONDS);
                        }
                    }
                }
            }.run();
            promise.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    tunnelChannel.remoteAddress = remoteAddress;
                    delayRef.set(5 * 1000);
                }
            });
        }

        private void sendRegister(ChannelHandlerContext ctx, InetSocketAddress remoteAddress) {
            PunchingProtos.PunchingMessage message = PunchingProtos.PunchingMessage.newBuilder()
                    .setChannelId(ctx.channel().id().asLongText())
                    .setType(PunchingProtos.MsgType.ReqRegisterType)
                    .setReqId(UUID.randomUUID().toString())
                    .build();
            Envelope envelope = Envelope.builder()
                    .recipient(remoteAddress)
                    .message(message)
                    .build();
            logger.debug("sendRegister: {}:{}", remoteAddress.getHostString(), remoteAddress.getPort());
            parent.writeAndFlush(envelope);
        }
    }

    private static class RegisterRespHandler extends ChannelInboundHandlerAdapter {

        private TunnelChannel tunnelChannel;
        private ChannelPromise promise;

        public RegisterRespHandler(TunnelChannel tunnelChannel, ChannelPromise promise) {
            this.tunnelChannel = tunnelChannel;
            this.promise = promise;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            Envelope<PunchingProtos.PunchingMessage> envelope = (Envelope<PunchingProtos.PunchingMessage>) msg;
            PunchingProtos.PunchingMessage request = envelope.message();
            switch (request.getType().getNumber()) {
                case PunchingProtos.MsgType.RespRegisterType_VALUE: {
                    PunchingProtos.ConnectionData connectionData = PunchingProtos.ConnectionData.parseFrom(request.getData());
                    InetSocketAddress localAddress = (InetSocketAddress) ctx.channel().localAddress();
                    logger.debug("recvRegister: {} -> {}:{}",
                            localAddress.getPort(),
                            connectionData.getHost(), connectionData.getPort());
                    tunnelChannel.connectionData = connectionData;
                    if (!promise.isDone()) {
                        promise.trySuccess();
                    }
                    break;
                }
                default: {
                    super.channelRead(ctx, msg);
                    break;
                }
            }
        }
    }

    public static class TunnelHandler extends ChannelInboundHandlerAdapter {

        private TunnelChannel tunnelChannel;

        public TunnelHandler(TunnelChannel tunnelChannel) {
            this.tunnelChannel = tunnelChannel;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            Envelope<PunchingProtos.PunchingMessage> envelope = (Envelope<PunchingProtos.PunchingMessage>) msg;
            PunchingProtos.PunchingMessage request = envelope.message();
            String streamId = request.getStreamId();
            if (StringUtils.isNotEmpty(streamId)) {
                StreamChannel streamChannel = StreamMap.get(streamId);
                if (null == streamChannel) {
                    streamChannel = (StreamChannel) StreamChannel.createServer(tunnelChannel).sync().channel();
                }
                streamChannel.receive(msg);
            }
        }
    }
}
