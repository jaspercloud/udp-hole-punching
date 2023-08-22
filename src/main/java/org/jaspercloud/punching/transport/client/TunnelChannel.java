package org.jaspercloud.punching.transport.client;

import io.netty.channel.*;
import org.jaspercloud.punching.proto.PunchingProtos;
import org.jaspercloud.punching.transport.BusChannel;
import org.jaspercloud.punching.transport.Envelope;
import org.jaspercloud.punching.transport.RemoteChannelId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class TunnelChannel extends BusChannel {

    private static Logger logger = LoggerFactory.getLogger(TunnelChannel.class);

    private static Map<String, CompletableFuture<PunchingProtos.PunchingMessage>> futureMap = new ConcurrentHashMap<>();
    private String nodeId;
    private String token;
    private PunchingProtos.ConnectionData connectionData;

    public String getNodeId() {
        return nodeId;
    }

    public String getToken() {
        return token;
    }

    public PunchingProtos.ConnectionData getConnectionData() {
        return connectionData;
    }

    @Override
    public SocketAddress localAddress() {
        return parent().localAddress();
    }

    private TunnelChannel(Channel channel, String nodeId, String token) {
        super(channel);
        this.nodeId = nodeId;
        this.token = token;
    }

    private TunnelChannel(Channel channel, ChannelId channelId) {
        super(channel, channelId);
    }

    static TunnelChannel create(Channel parent, String channelId, ChannelInitializer<Channel> initializer) throws InterruptedException {
        TunnelChannel tunnelChannel = new TunnelChannel(parent, new RemoteChannelId(channelId));
        tunnelChannel.pipeline().addLast("init", new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ChannelPipeline pipeline = ch.pipeline();
                pipeline.addLast("tunnel", new TunnelHandler(tunnelChannel));
                pipeline.addLast(initializer);
            }
        });
        parent.eventLoop().register(tunnelChannel).sync();
        return tunnelChannel;
    }

    public static TunnelChannel createNode(Channel parent, String nodeId, String token) throws InterruptedException {
        TunnelChannel tunnelChannel = new TunnelChannel(parent, nodeId, token);
        tunnelChannel.pipeline().addLast("init", new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ChannelPipeline pipeline = ch.pipeline();
                pipeline.addLast("registerReq", new RegisterReqHandler(parent, tunnelChannel));
                pipeline.addLast("tunnel", new TunnelHandler(tunnelChannel));
            }
        });
        parent.eventLoop().register(tunnelChannel).sync();
        return tunnelChannel;
    }

    @Override
    public ChannelFuture writeAndFlush(Object msg) {
        return parent().writeAndFlush(msg);
    }

    public PunchingProtos.NodeData queryNode(String nodeId, String token, long timeout) throws Exception {
        String id = UUID.randomUUID().toString();
        CompletableFuture<PunchingProtos.PunchingMessage> future = new CompletableFuture<>();
        futureMap.put(id, future);
        try {
            PunchingProtos.NodeData nodeData = PunchingProtos.NodeData.newBuilder()
                    .setNodeId(nodeId)
                    .setToken(token)
                    .build();
            PunchingProtos.PunchingMessage message = PunchingProtos.PunchingMessage.newBuilder()
                    .setChannelId(id().asLongText())
                    .setStreamId(parent().id().asLongText())
                    .setType(PunchingProtos.MsgType.ReqQueryNode)
                    .setReqId(id)
                    .setData(nodeData.toByteString())
                    .build();
            Envelope envelope = Envelope.builder()
                    .recipient((InetSocketAddress) remoteAddress())
                    .message(message)
                    .build();
            parent().writeAndFlush(envelope);
            PunchingProtos.PunchingMessage respMessage = future.get(timeout, TimeUnit.MILLISECONDS);
            PunchingProtos.NodeData respNodeData = PunchingProtos.NodeData.parseFrom(respMessage.getData());
            return respNodeData;
        } finally {
            futureMap.remove(id);
        }
    }

    private static class TunnelHandler extends ChannelInboundHandlerAdapter {

        private TunnelChannel tunnelChannel;

        public TunnelHandler(TunnelChannel tunnelChannel) {
            this.tunnelChannel = tunnelChannel;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            Envelope<PunchingProtos.PunchingMessage> envelope = (Envelope<PunchingProtos.PunchingMessage>) msg;
            PunchingProtos.PunchingMessage request = envelope.message();
            switch (request.getType().getNumber()) {
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
                    tunnelChannel.writeAndFlush(data);
                    break;
                }
                case PunchingProtos.MsgType.RespQueryNode_VALUE: {
                    CompletableFuture<PunchingProtos.PunchingMessage> future = futureMap.remove(request.getReqId());
                    if (null != future) {
                        future.complete(request);
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

    private static class RegisterReqHandler extends ChannelDuplexHandler {

        private Channel parent;
        private TunnelChannel tunnelChannel;

        public RegisterReqHandler(Channel parent, TunnelChannel tunnelChannel) {
            this.parent = parent;
            this.tunnelChannel = tunnelChannel;
        }

        @Override
        public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) throws Exception {
            ChannelPromise channelPromise = ctx.newPromise();
            Channel channel = ctx.channel();
            channel.pipeline().addAfter("registerReq", "registerResp", new RegisterRespHandler(tunnelChannel, channelPromise));
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
            channelPromise.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    delayRef.set(5 * 1000);
                    ctx.connect(remoteAddress, localAddress, promise);
                }
            });
        }

        private void sendRegister(ChannelHandlerContext ctx, InetSocketAddress remoteAddress) {
            PunchingProtos.NodeData nodeData = PunchingProtos.NodeData.newBuilder()
                    .setNodeId(tunnelChannel.getNodeId())
                    .setToken(tunnelChannel.getToken())
                    .build();
            PunchingProtos.PunchingMessage message = PunchingProtos.PunchingMessage.newBuilder()
                    .setChannelId(ctx.channel().id().asLongText())
                    .setType(PunchingProtos.MsgType.ReqRegisterType)
                    .setReqId(UUID.randomUUID().toString())
                    .setData(nodeData.toByteString())
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
}
