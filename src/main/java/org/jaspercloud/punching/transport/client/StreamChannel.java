package org.jaspercloud.punching.transport.client;

import com.google.protobuf.ByteString;
import io.netty.channel.*;
import org.jaspercloud.punching.proto.PunchingProtos;
import org.jaspercloud.punching.transport.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class StreamChannel extends BusChannel {

    private static Logger logger = LoggerFactory.getLogger(StreamChannel.class);

    public StreamChannel(Channel parent) {
        super(parent);
    }

    public StreamChannel(Channel parent, ChannelId channelId) {
        super(parent, channelId);
    }

    @Override
    public SocketAddress localAddress() {
        return parent().localAddress();
    }

    static StreamChannel create(TunnelChannel parent, String id, ChannelInitializer<Channel> initializer) throws InterruptedException {
        StreamChannel streamChannel = new StreamChannel(parent, new RemoteChannelId(id));
        streamChannel.pipeline().addLast("init", new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ChannelPipeline pipeline = ch.pipeline();
                pipeline.addLast("rewrite", new ReWriteHandler(parent));
                pipeline.addLast("reSend", new ReSendHandler(20, 3000));
                pipeline.addLast("stream", new StreamHandler());
                pipeline.addLast(initializer);
            }
        });
        parent.eventLoop().register(streamChannel).sync();
        return streamChannel;
    }

    public static StreamChannel createClient(TunnelChannel parent) throws InterruptedException {
        StreamChannel streamChannel = new StreamChannel(parent);
        streamChannel.pipeline().addLast("init", new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ChannelPipeline pipeline = ch.pipeline();
                pipeline.addLast("rewrite", new ReWriteHandler(parent));
                pipeline.addLast("reSend", new ReSendHandler(20, 3000));
                pipeline.addLast("ping", new PingHandler(parent));
                pipeline.addLast("stream", new StreamHandler());
            }
        });
        parent.eventLoop().register(streamChannel).sync();
        return streamChannel;
    }

    private static class PingHandler extends ChannelDuplexHandler {

        private TunnelChannel parent;

        public PingHandler(TunnelChannel parent) {
            this.parent = parent;
        }

        @Override
        public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) throws Exception {
            AtomicReference<InetSocketAddress> remoteAddressRef = new AtomicReference<>((InetSocketAddress) remoteAddress);
            AtomicReference<Long> delayTimeRef = new AtomicReference<>(20L);
            Channel channel = ctx.channel();
            ChannelPromise channelPromise = ctx.newPromise();
            ctx.pipeline().addAfter("ping", "pong", new PongHandler(remoteAddressRef, channelPromise));
            channelPromise.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    delayTimeRef.set(5 * 1000L);
                    ctx.connect(remoteAddressRef.get(), localAddress, promise);
                }
            });
            new Runnable() {
                @Override
                public void run() {
                    try {
                        writePing(ctx, remoteAddressRef.get());
                        if (!channelPromise.isDone()) {
                            writeRelayPunching(ctx, remoteAddressRef.get());
                        }
                    } finally {
                        if (channel.isActive()) {
                            channel.eventLoop().schedule(this, delayTimeRef.get(), TimeUnit.MILLISECONDS);
                        }
                    }
                }
            }.run();
        }

        private void writePing(ChannelHandlerContext ctx, InetSocketAddress remoteAddress) {
            Channel channel = ctx.channel();
            PunchingProtos.PunchingMessage message = PunchingProtos.PunchingMessage.newBuilder()
                    .setChannelId(parent.id().asLongText())
                    .setStreamId(channel.id().asLongText())
                    .setType(PunchingProtos.MsgType.PingType)
                    .setReqId(UUID.randomUUID().toString())
                    .build();
            Envelope envelope = Envelope.builder()
                    .recipient(remoteAddress)
                    .message(message)
                    .build();
            logger.debug("sendPing: {}:{}", remoteAddress.getHostString(), remoteAddress.getPort());
            ctx.writeAndFlush(envelope);
        }

        private void writeRelayPunching(ChannelHandlerContext ctx, InetSocketAddress remoteAddress) {
            Channel channel = ctx.channel();
            PunchingProtos.ConnectionData connectionData = parent.getConnectionData();
            PunchingProtos.PunchingData punchingData = PunchingProtos.PunchingData.newBuilder()
                    .setPingHost(connectionData.getHost())
                    .setPingPort(connectionData.getPort())
                    .setPongHost(remoteAddress.getHostString())
                    .setPongPort(remoteAddress.getPort())
                    .build();
            PunchingProtos.PunchingMessage message = PunchingProtos.PunchingMessage.newBuilder()
                    .setChannelId(parent.id().asLongText())
                    .setStreamId(channel.id().asLongText())
                    .setType(PunchingProtos.MsgType.ReqRelayPunchingType)
                    .setReqId(UUID.randomUUID().toString())
                    .setData(punchingData.toByteString())
                    .build();
            InetSocketAddress tunnelAddress = (InetSocketAddress) parent.remoteAddress();
            Envelope envelope = Envelope.builder()
                    .recipient(new InetSocketAddress(tunnelAddress.getHostString(), tunnelAddress.getPort()))
                    .message(message)
                    .reSend(true)
                    .build();
            logger.debug("relayPunching: {}:{}", tunnelAddress.getHostString(), tunnelAddress.getPort());
            ctx.writeAndFlush(envelope);
        }
    }

    private static class PongHandler extends ChannelInboundHandlerAdapter {

        private AtomicReference<InetSocketAddress> remoteAddressRef;
        private ChannelPromise promise;

        public PongHandler(AtomicReference<InetSocketAddress> remoteAddressRef, ChannelPromise promise) {
            this.remoteAddressRef = remoteAddressRef;
            this.promise = promise;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            Envelope<PunchingProtos.PunchingMessage> envelope = (Envelope<PunchingProtos.PunchingMessage>) msg;
            PunchingProtos.PunchingMessage request = envelope.message();
            InetSocketAddress sender = envelope.sender();
            switch (request.getType().getNumber()) {
                case PunchingProtos.MsgType.PongType_VALUE: {
                    String host = sender.getHostString();
                    int port = sender.getPort();
                    logger.debug("recvPong: {}:{}", host, port);
                    remoteAddressRef.set(sender);
                    if (!promise.isDone()) {
                        promise.trySuccess();
                    }
                    break;
                }
                case PunchingProtos.MsgType.RespRelayPunchingType_VALUE: {
                    remoteAddressRef.set(sender);
                    break;
                }
                default: {
                    super.channelRead(ctx, msg);
                    break;
                }
            }
        }
    }

    private static class StreamHandler extends ChannelDuplexHandler {

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            Envelope<PunchingProtos.PunchingMessage> envelope = (Envelope<PunchingProtos.PunchingMessage>) msg;
            PunchingProtos.PunchingMessage request = envelope.message();
            switch (request.getType().getNumber()) {
                case PunchingProtos.MsgType.PingType_VALUE: {
                    PunchingProtos.PunchingMessage message = PunchingProtos.PunchingMessage.newBuilder()
                            .setChannelId(request.getChannelId())
                            .setStreamId(request.getStreamId())
                            .setType(PunchingProtos.MsgType.PongType)
                            .setReqId(request.getReqId())
                            .setData(ByteString.EMPTY)
                            .build();
                    Envelope data = Envelope.builder()
                            .recipient(envelope.sender())
                            .message(message)
                            .build();
                    ctx.writeAndFlush(data);
                    break;
                }
                case PunchingProtos.MsgType.Data_VALUE: {
                    byte[] bytes = envelope.message().getData().toByteArray();
                    super.channelRead(ctx, bytes);
                    break;
                }
                default: {
                    super.channelRead(ctx, msg);
                    break;
                }
            }
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            Channel channel = ctx.channel();
            Envelope envelope;
            if (msg instanceof String) {
                String data = (String) msg;
                PunchingProtos.PunchingMessage message = PunchingProtos.PunchingMessage.newBuilder()
                        .setChannelId(channel.parent().id().asLongText())
                        .setStreamId(channel.id().asLongText())
                        .setType(PunchingProtos.MsgType.Data)
                        .setReqId(UUID.randomUUID().toString())
                        .setData(ByteString.copyFrom(data.getBytes(StandardCharsets.UTF_8)))
                        .build();
                envelope = Envelope.builder()
                        .recipient((InetSocketAddress) channel.remoteAddress())
                        .message(message)
                        .build();
            } else if (msg instanceof byte[]) {
                byte[] data = (byte[]) msg;
                PunchingProtos.PunchingMessage message = PunchingProtos.PunchingMessage.newBuilder()
                        .setChannelId(channel.parent().id().asLongText())
                        .setStreamId(channel.id().asLongText())
                        .setType(PunchingProtos.MsgType.Data)
                        .setReqId(UUID.randomUUID().toString())
                        .setData(ByteString.copyFrom(data))
                        .build();
                envelope = Envelope.builder()
                        .recipient((InetSocketAddress) channel.remoteAddress())
                        .message(message)
                        .build();
            } else {
                throw new UnsupportedOperationException();
            }
            ctx.writeAndFlush(envelope, promise);
        }
    }
}
