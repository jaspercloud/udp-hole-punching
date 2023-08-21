package org.jaspercloud.punching.transport;

import com.google.protobuf.ByteString;
import io.netty.channel.*;
import org.jaspercloud.punching.proto.PunchingProtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class StreamChannel extends BusChannel {

    private static Logger logger = LoggerFactory.getLogger(TunnelChannel.class);

    public StreamChannel(Channel parent) {
        super(parent);
    }

    //
//    private TunnelChannel tunnelChannel;
//    private String id;
//    private String host;
//    private int port;
//    private boolean active;
//    private ChannelPromise connectPromise;
//    private ScheduledFuture<?> pingFuture;
//    private ScheduledFuture<?> relayPunchingSchedule;
//
//    public String getId() {
//        return id;
//    }
//
//    public boolean isActive() {
//        return active;
//    }
//
//    private StreamChannel(TunnelChannel tunnelChannel, String id, String host, int port) {
//        this.tunnelChannel = tunnelChannel;
//        this.id = id;
//        this.host = host;
//        this.port = port;
//        tunnelChannel.addStreamChannel(this);
//    }
//
    public static ChannelFuture createServer(TunnelChannel parent) throws InterruptedException {
        StreamChannel streamChannel = new StreamChannel(parent);
        parent.addStreamChannel(streamChannel);
        streamChannel.pipeline().addLast("init", new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ch.pipeline().addLast("stream", new StreamHandler(parent));
            }
        });
        ChannelFuture channelFuture = parent.eventLoop().register(streamChannel);
        return channelFuture;
    }

    public static ChannelFuture createClient(TunnelChannel parent) throws InterruptedException {
        StreamChannel streamChannel = new StreamChannel(parent);
        parent.addStreamChannel(streamChannel);
        streamChannel.pipeline().addLast("init", new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ch.pipeline().addLast("ping", new PingHandler(parent));
                ch.pipeline().addLast("stream", new StreamHandler(parent));
            }
        });
        ChannelFuture channelFuture = parent.eventLoop().register(streamChannel);
        return channelFuture;
    }

    private static class PingHandler extends ChannelDuplexHandler {

        private TunnelChannel parent;

        public PingHandler(TunnelChannel parent) {
            this.parent = parent;
        }

        @Override
        public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) throws Exception {
            Channel channel = ctx.channel();
            AtomicReference<InetSocketAddress> remoteAddressRef = new AtomicReference<>((InetSocketAddress) remoteAddress);
            ctx.pipeline().addAfter("ping", "pong", new PongHandler(parent, remoteAddressRef, promise));
            AtomicReference<Integer> delayRef = new AtomicReference<>(100);
            new Runnable() {
                @Override
                public void run() {
                    try {
                        writePing(ctx, remoteAddressRef.get());
                        if (!promise.isDone()) {
                            writeRelayPunching(ctx, remoteAddressRef.get());
                        }
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
                    delayRef.set(5 * 1000);
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

    private static class StreamHandler extends ChannelInboundHandlerAdapter {

        private TunnelChannel parent;

        public StreamHandler(TunnelChannel parent) {
            this.parent = parent;
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
                case PunchingProtos.MsgType.ReqRelayPunchingType_VALUE: {
                    PunchingProtos.PunchingData punchingData = PunchingProtos.PunchingData.parseFrom(request.getData());
                    logger.debug("recvReqPunching: {}:{} -> {}:{}",
                            punchingData.getPingHost(), punchingData.getPingPort(),
                            punchingData.getPongHost(), punchingData.getPongPort());
                    PunchingProtos.PunchingMessage message = PunchingProtos.PunchingMessage.newBuilder()
                            .setChannelId(request.getChannelId())
                            .setStreamId(request.getStreamId())
                            .setType(PunchingProtos.MsgType.RespRelayPunchingType)
                            .setReqId(request.getReqId())
                            .build();
                    InetSocketAddress address = new InetSocketAddress(punchingData.getPingHost(), punchingData.getPingPort());
                    Envelope data = Envelope.builder()
                            .recipient(address)
                            .message(message)
                            .build();
                    parent.writeAndFlush(data);
                    break;
                }
            }
        }
    }
//
//    void receive(Envelope<PunchingProtos.PunchingMessage> message) {
//        PunchingProtos.PunchingMessage request = message.message();
//        InetSocketAddress sender = message.sender();
//        switch (request.getType().getNumber()) {
//            case PunchingProtos.MsgType.PongType_VALUE: {
//                this.host = sender.getHostString();
//                this.port = sender.getPort();
//                logger.debug("recvPong: {}:{}", host, port);
//                setConnected();
//                break;
//            }
//            case PunchingProtos.MsgType.RespRelayPunchingType_VALUE: {
//                String host = sender.getHostString();
//                int port = sender.getPort();
//                logger.debug("recvRespPunching: {}:{}", host, port);
//                this.host = host;
//                this.port = port;
//                break;
//            }
//        }
//    }
//
//    private void setConnected() {
//        if (null != connectPromise && !connectPromise.isDone()) {
//            connectPromise.trySuccess();
//            active = true;
//        }
//    }
//
//    public void connect(long timeout) throws InterruptedException, ExecutionException, TimeoutException {
//        connectPromise = tunnelChannel.newPromise();
//        pingFuture = tunnelChannel.eventLoop().scheduleAtFixedRate(() -> {
//            writePing();
//        }, 0, 100, TimeUnit.MILLISECONDS);
//        relayPunchingSchedule = tunnelChannel.eventLoop().scheduleAtFixedRate(() -> {
//            writeRelayPunching();
//        }, 0, 100, TimeUnit.MILLISECONDS);
//        try {
//            connectPromise.get(timeout, TimeUnit.MILLISECONDS);
//        } catch (Throwable e) {
//            close();
//            throw e;
//        } finally {
//            pingFuture.cancel(true);
//            relayPunchingSchedule.cancel(true);
//        }
//        pingFuture = tunnelChannel.eventLoop().scheduleAtFixedRate(() -> {
//            writePing();
//        }, 0, 5 * 1000, TimeUnit.MILLISECONDS);
//    }
//
//    private void close() {
//        if (null != pingFuture) {
//            pingFuture.cancel(true);
//        }
//        tunnelChannel.removeStreamChannel(this);
//        active = false;
//    }
//
//    private void writePing() {
//        PunchingProtos.PunchingMessage message = PunchingProtos.PunchingMessage.newBuilder()
//                .setChannelId(tunnelChannel.getId())
//                .setStreamId(id)
//                .setType(PunchingProtos.MsgType.PingType)
//                .setReqId(UUID.randomUUID().toString())
//                .build();
//        Envelope envelope = Envelope.builder()
//                .recipient(new InetSocketAddress(host, port))
//                .message(message)
//                .build();
//        logger.debug("sendPing: {}:{}", host, port);
//        tunnelChannel.writeAndFlush(envelope);
//    }
//
//    private void writeRelayPunching() {
//        PunchingProtos.ConnectionData connectionData = tunnelChannel.getConnectionData();
//        PunchingProtos.PunchingData punchingData = PunchingProtos.PunchingData.newBuilder()
//                .setPingHost(connectionData.getHost())
//                .setPingPort(connectionData.getPort())
//                .setPongHost(host)
//                .setPongPort(port)
//                .build();
//        PunchingProtos.PunchingMessage message = PunchingProtos.PunchingMessage.newBuilder()
//                .setChannelId(tunnelChannel.getId())
//                .setStreamId(id)
//                .setType(PunchingProtos.MsgType.ReqRelayPunchingType)
//                .setReqId(UUID.randomUUID().toString())
//                .setData(punchingData.toByteString())
//                .build();
//        logger.debug("relayPunching: {}:{}", host, port);
//        tunnelChannel.writeRelayData(message);
//    }

}
