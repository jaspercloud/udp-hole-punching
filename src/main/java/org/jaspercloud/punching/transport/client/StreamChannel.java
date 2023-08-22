package org.jaspercloud.punching.transport.client;

import com.google.protobuf.ByteString;
import io.netty.channel.*;
import org.jaspercloud.punching.proto.PunchingProtos;
import org.jaspercloud.punching.transport.BusChannel;
import org.jaspercloud.punching.transport.Envelope;
import org.jaspercloud.punching.transport.RemoteChannelId;
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

    static StreamChannel create(TunnelChannel parent, String id, ChannelInitializer<Channel> initializer) throws InterruptedException {
        StreamChannel streamChannel = new StreamChannel(parent, new RemoteChannelId(id));
        streamChannel.pipeline().addLast("init", new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ChannelPipeline pipeline = ch.pipeline();
                pipeline.addLast("write", new WriteHandler(parent));
                pipeline.addLast("stream", new StreamHandler(parent, streamChannel));
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
                pipeline.addLast("ping", new PingHandler(parent, streamChannel));
                pipeline.addLast("write", new WriteHandler(parent));
                pipeline.addLast("stream", new StreamHandler(parent, streamChannel));
            }
        });
        parent.eventLoop().register(streamChannel).sync();
        return streamChannel;
    }

    private static class PingHandler extends ChannelDuplexHandler {

        private TunnelChannel parent;
        private StreamChannel streamChannel;

        public PingHandler(TunnelChannel parent, StreamChannel streamChannel) {
            this.parent = parent;
            this.streamChannel = streamChannel;
        }

        @Override
        public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) throws Exception {
            ChannelPromise channelPromise = ctx.newPromise();
            Channel channel = ctx.channel();
            AtomicReference<InetSocketAddress> remoteAddressRef = new AtomicReference<>((InetSocketAddress) remoteAddress);
            ctx.pipeline().addAfter("ping", "pong", new PongHandler(parent, remoteAddressRef, channelPromise));
            AtomicReference<Integer> delayRef = new AtomicReference<>(100);
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
                            channel.eventLoop().schedule(this, delayRef.get(), TimeUnit.MILLISECONDS);
                        }
                    }
                }
            }.run();
            channelPromise.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    delayRef.set(5 * 1000);
                    ctx.connect(remoteAddressRef.get(), localAddress, promise);
                }
            });
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
            parent.writeAndFlush(envelope);
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
                    .build();
            logger.debug("relayPunching: {}:{}", tunnelAddress.getHostString(), tunnelAddress.getPort());
            parent.writeAndFlush(envelope);
        }
    }

    private static class PongHandler extends ChannelInboundHandlerAdapter {

        private TunnelChannel parent;
        private AtomicReference<InetSocketAddress> remoteAddressRef;
        private ChannelPromise promise;

        public PongHandler(TunnelChannel parent, AtomicReference<InetSocketAddress> remoteAddressRef, ChannelPromise promise) {
            this.parent = parent;
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

    private static class WriteHandler extends ChannelOutboundHandlerAdapter {

        private TunnelChannel parent;

        public WriteHandler(TunnelChannel parent) {
            this.parent = parent;
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            parent.writeAndFlush(msg);
        }
    }

    private static class StreamHandler extends ChannelDuplexHandler {

        private TunnelChannel parent;
        private StreamChannel streamChannel;

        public StreamHandler(TunnelChannel parent, StreamChannel streamChannel) {
            this.parent = parent;
            this.streamChannel = streamChannel;
        }

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
                    parent.writeAndFlush(data);
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
