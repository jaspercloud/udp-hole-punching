package org.jaspercloud.punching.transport;

import com.google.protobuf.ByteString;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.concurrent.ScheduledFuture;
import org.jaspercloud.punching.proto.PunchingProtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class PunchingLocalConnection implements PunchingConnection {

    private Logger logger = LoggerFactory.getLogger(getClass());

    private PunchingClient punchingClient;
    private PunchingConnectionHandler handler;
    private String id;
    private String punchingHost;
    private volatile int punchingPort;
    private volatile boolean active;
    private ChannelPromise promise;
    private ScheduledFuture<?> pingFuture;
    private ScheduledFuture<?> relayPunchingSchedule;
    private volatile long pingTime = System.currentTimeMillis();

    public PunchingLocalConnection(PunchingClient punchingClient,
                                   PunchingConnectionHandler handler,
                                   String id,
                                   String host,
                                   int port) {
        this.punchingClient = punchingClient;
        this.handler = handler;
        this.id = id;
        this.punchingHost = host;
        this.punchingPort = port;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public long getPingTime() {
        return pingTime;
    }

    @Override
    public InetSocketAddress localAddress() {
        return new InetSocketAddress(punchingClient.getLocalHost(), punchingClient.getLocalPort());
    }

    @Override
    public InetSocketAddress remoteAddress() {
        return new InetSocketAddress(punchingHost, punchingPort);
    }

    void active() {
        active = true;
        pingTime = System.currentTimeMillis();
        if (!promise.isDone()) {
            promise.setSuccess();
        }
    }

    void updatePunchingPort(int port) {
        punchingPort = port;
    }

    void onChannelRead(ChannelHandlerContext ctx, Envelope<PunchingProtos.PunchingMessage> envelope) throws Exception {
        PunchingProtos.PunchingMessage request = envelope.message();
        switch (request.getType().getNumber()) {
            case PunchingProtos.MsgType.Data_VALUE: {
                handler.onRead(this, request.getData().toByteArray());
                break;
            }
        }
    }

    @Override
    public void connect(long timeout) throws TimeoutException, ExecutionException, InterruptedException {
        Channel channel = punchingClient.getChannel();
        promise = channel.newPromise();
        pingFuture = channel.eventLoop().scheduleAtFixedRate(() -> {
            writePing();
        }, 0, 100, TimeUnit.MILLISECONDS);
        relayPunchingSchedule = channel.eventLoop().scheduleAtFixedRate(() -> {
            writeRelayPunching();
        }, 0, 100, TimeUnit.MILLISECONDS);
        try {
            promise.get(timeout, TimeUnit.MILLISECONDS);
        } finally {
            pingFuture.cancel(true);
            relayPunchingSchedule.cancel(true);
        }
        handler.onActive(this);
        pingFuture = channel.eventLoop().scheduleAtFixedRate(() -> {
            writePing();
        }, 0, 5000, TimeUnit.MILLISECONDS);
    }

    @Override
    public void close() {
        if (null != pingFuture) {
            pingFuture.cancel(true);
        }
        if (null != relayPunchingSchedule) {
            relayPunchingSchedule.cancel(true);
        }
        active = false;
        handler.onInActive(this);
    }

    private void writePing() {
        Channel channel = punchingClient.getChannel();
        PunchingProtos.PunchingMessage message = PunchingProtos.PunchingMessage.newBuilder()
                .setChannelId(id)
                .setType(PunchingProtos.MsgType.PingType)
                .setReqId(UUID.randomUUID().toString())
                .build();
        Envelope envelope = Envelope.builder()
                .recipient(new InetSocketAddress(punchingHost, punchingPort))
                .message(message)
                .build();
        logger.debug("sendPing: {}:{}", envelope.recipient().getHostString(), envelope.recipient().getPort());
        channel.writeAndFlush(envelope);
    }

    private void writeRelayPunching() {
        Channel channel = punchingClient.getChannel();
        PunchingProtos.ConnectionData connectionData = (PunchingProtos.ConnectionData) AttributeKeyUtil.connectionData(channel).get();
        PunchingProtos.PunchingData punchingData = PunchingProtos.PunchingData.newBuilder()
                .setPingHost(connectionData.getHost())
                .setPingPort(connectionData.getPort())
                .setPongHost(punchingHost)
                .setPongPort(punchingPort)
                .build();
        PunchingProtos.PunchingMessage message = PunchingProtos.PunchingMessage.newBuilder()
                .setChannelId(id)
                .setType(PunchingProtos.MsgType.ReqRelayPunchingType)
                .setReqId(UUID.randomUUID().toString())
                .setData(punchingData.toByteString())
                .build();
        Envelope envelope = Envelope.builder()
                .recipient(new InetSocketAddress(punchingClient.getServerHost(), punchingClient.getServerPort()))
                .message(message)
                .build();
        logger.debug("relayPunching: {}:{}", punchingClient.getServerHost(), punchingClient.getServerPort());
        channel.writeAndFlush(envelope);
    }

    @Override
    public ChannelFuture writeAndFlush(byte[] data) {
        PunchingProtos.PunchingMessage message = PunchingProtos.PunchingMessage.newBuilder()
                .setChannelId(id)
                .setType(PunchingProtos.MsgType.Data)
                .setReqId(UUID.randomUUID().toString())
                .setData(ByteString.copyFrom(data))
                .build();
        Envelope<PunchingProtos.PunchingMessage> envelope = Envelope.<PunchingProtos.PunchingMessage>builder()
                .recipient(new InetSocketAddress(punchingHost, punchingPort))
                .message(message)
                .build();
        InetSocketAddress recipient = envelope.recipient();
        logger.debug("sendData: {}:{}", recipient.getHostString(), recipient.getPort());
        ChannelFuture future = punchingClient.writeAndFlush(envelope);
        return future;
    }
}
