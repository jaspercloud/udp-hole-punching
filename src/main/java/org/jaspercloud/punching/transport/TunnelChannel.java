package org.jaspercloud.punching.transport;

import io.netty.channel.*;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.concurrent.ScheduledFuture;
import org.apache.commons.lang3.StringUtils;
import org.jaspercloud.punching.proto.PunchingProtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class TunnelChannel {

    private static Logger logger = LoggerFactory.getLogger(TunnelChannel.class);

    private Channel channel;
    private String id;
    private String host;
    private int port;
    private boolean active;
    private ChannelPromise connectPromise;
    private ScheduledFuture<?> registerFuture;
    private PunchingProtos.ConnectionData connectionData;

    private static Map<String, TunnelChannel> TunnelMap = new ConcurrentHashMap<>();
    private Map<String, StreamChannel> streamMap = new ConcurrentHashMap<>();

    public void addStreamChannel(StreamChannel channel) {
        streamMap.put(channel.getId(), channel);
    }

    public void removeStreamChannel(StreamChannel channel) {
        streamMap.remove(channel.getId());
    }

    public String getId() {
        return id;
    }

    public PunchingProtos.ConnectionData getConnectionData() {
        return connectionData;
    }

    public boolean isActive() {
        return active;
    }

    private void setConnected() {
        if (null != connectPromise && !connectPromise.isDone()) {
            connectPromise.trySuccess();
            active = true;
        }
    }

    private void setConnectionData(PunchingProtos.ConnectionData connectionData) {
        this.connectionData = connectionData;
    }

    private TunnelChannel(Channel channel, String id, String host, int port) {
        this.channel = channel;
        this.id = id;
        this.host = host;
        this.port = port;
    }

    public static SimpleChannelInboundHandler<Envelope<PunchingProtos.PunchingMessage>> createHandler() {
        SimpleChannelInboundHandler<Envelope<PunchingProtos.PunchingMessage>> handler = new SimpleChannelInboundHandler<Envelope<PunchingProtos.PunchingMessage>>() {
            @Override
            protected void channelRead0(ChannelHandlerContext ctx, Envelope<PunchingProtos.PunchingMessage> msg) throws Exception {
                PunchingProtos.PunchingMessage request = msg.message();
                TunnelChannel tunnelChannel = TunnelMap.get(request.getChannelId());
                if (null != tunnelChannel) {
                    switch (request.getType().getNumber()) {
                        case PunchingProtos.MsgType.RespRegisterType_VALUE: {
                            PunchingProtos.ConnectionData connectionData = PunchingProtos.ConnectionData.parseFrom(request.getData());
                            InetSocketAddress localAddress = (InetSocketAddress) ctx.channel().localAddress();
                            logger.debug("recvRegister: {} -> {}:{}",
                                    localAddress.getPort(),
                                    connectionData.getHost(), connectionData.getPort());
                            tunnelChannel.setConnected();
                            tunnelChannel.setConnectionData(connectionData);
                            break;
                        }
                        default: {
                            String streamId = request.getStreamId();
                            if (StringUtils.isNotEmpty(streamId)) {
                                StreamChannel streamChannel = tunnelChannel.streamMap.get(streamId);
                                if (null != streamChannel) {
                                    streamChannel.receive(msg);
                                }
                            }
                        }
                    }
                }
            }
        };
        return handler;
    }

    public static TunnelChannel create(Channel channel, String host, int port) {
        return new TunnelChannel(channel, UUID.randomUUID().toString(), host, port);
    }

    public void connect(long timeout) throws ExecutionException, InterruptedException, TimeoutException {
        TunnelMap.put(id, this);
        try {
            connectPromise = channel.newPromise();
            registerFuture = channel.eventLoop().scheduleAtFixedRate(() -> {
                writeRegister();
            }, 0, 100, TimeUnit.MILLISECONDS);
            connectPromise.get(timeout, TimeUnit.MILLISECONDS);
        } catch (Throwable e) {
            close();
            throw e;
        }
        registerFuture.cancel(true);
        registerFuture = channel.eventLoop().scheduleAtFixedRate(() -> {
            writeRegister();
        }, 0, 5 * 1000, TimeUnit.MILLISECONDS);
    }

    public void close() {
        if (null != registerFuture) {
            registerFuture.cancel(true);
        }
        active = false;
        TunnelMap.remove(id);
    }

    private void writeRegister() {
        PunchingProtos.PunchingMessage message = PunchingProtos.PunchingMessage.newBuilder()
                .setChannelId(id)
                .setType(PunchingProtos.MsgType.ReqRegisterType)
                .setReqId(UUID.randomUUID().toString())
                .build();
        Envelope envelope = Envelope.builder()
                .recipient(new InetSocketAddress(host, port))
                .message(message)
                .build();
        logger.debug("sendRegister: {}:{}", host, port);
        channel.writeAndFlush(envelope);
    }

    public ChannelFuture writeAndFlush(Object msg) {
        ChannelFuture future = channel.writeAndFlush(msg);
        return future;
    }

    public void writeRelayData(PunchingProtos.PunchingMessage message) {
        Envelope envelope = Envelope.builder()
                .recipient(new InetSocketAddress(host, port))
                .message(message)
                .build();
        channel.writeAndFlush(envelope);
    }

    public EventLoop eventLoop() {
        return channel.eventLoop();
    }

    public ChannelPromise newPromise() {
        ChannelPromise promise = channel.newPromise();
        return promise;
    }
}
