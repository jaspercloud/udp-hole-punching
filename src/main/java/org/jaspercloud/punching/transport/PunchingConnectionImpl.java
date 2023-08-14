package org.jaspercloud.punching.transport;

import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.concurrent.ScheduledFuture;
import org.jaspercloud.punching.proto.PunchingProtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class PunchingConnectionImpl implements PunchingConnection {

    private Logger logger = LoggerFactory.getLogger(getClass());

    private PunchingClient punchingClient;
    private String id;
    private String punchingHost;
    private int punchingPort;
    private boolean active;
    private ChannelPromise promise;
    private ScheduledFuture<?> pingFuture;
    private ScheduledFuture<?> relayPunchingSchedule;
    private ScheduledFuture<?> checkHeartFuture;
    private long pingTime = System.currentTimeMillis();

    public PunchingConnectionImpl(PunchingClient punchingClient,
                                  String id,
                                  String host,
                                  int port) {
        this.punchingClient = punchingClient;
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
    public void onChannelRead(ChannelHandlerContext ctx, AddressedEnvelope<PunchingProtos.PunchingMessage, InetSocketAddress> envelope) throws Exception {
        switch (envelope.content().getType().getNumber()) {
            case PunchingProtos.MsgType.PingType_VALUE: {
                logger.debug("recvPing: {}:{}", envelope.sender().getHostString(), envelope.sender().getPort());
                PunchingProtos.PunchingMessage message = PunchingProtos.PunchingMessage.newBuilder()
                        .setType(PunchingProtos.MsgType.PongType)
                        .setReqId(envelope.content().getReqId())
                        .build();
                ByteBuf byteBuf = ProtosUtil.toBuffer(ctx.alloc(), message);
                DatagramPacket data = new DatagramPacket(byteBuf, envelope.sender());
                ctx.writeAndFlush(data);
                break;
            }
            case PunchingProtos.MsgType.PongType_VALUE: {
                String host = envelope.sender().getHostString();
                int port = envelope.sender().getPort();
                logger.debug("recvPong: {}:{}", host, port);
                active = true;
                pingTime = System.currentTimeMillis();
                if (!promise.isDone()) {
                    promise.setSuccess();
                }
                break;
            }
            case PunchingProtos.MsgType.ReqRelayPunchingType_VALUE: {
                PunchingProtos.PunchingData punchingData = PunchingProtos.PunchingData.parseFrom(envelope.content().getData());
                logger.debug("recvReqPunching: {}:{} -> {}:{}",
                        punchingData.getPingHost(), punchingData.getPingPort(),
                        punchingData.getPongHost(), punchingData.getPongPort());
                PunchingProtos.PunchingMessage message = PunchingProtos.PunchingMessage.newBuilder()
                        .setType(PunchingProtos.MsgType.RespRelayPunchingType)
                        .setReqId(envelope.content().getReqId())
                        .build();
                ByteBuf byteBuf = ProtosUtil.toBuffer(ctx.alloc(), message);
                DatagramPacket data = new DatagramPacket(byteBuf, new InetSocketAddress(punchingData.getPingHost(), punchingData.getPingPort()));
                ctx.writeAndFlush(data);
                break;
            }
            case PunchingProtos.MsgType.RespRelayPunchingType_VALUE: {
                String host = envelope.sender().getHostString();
                int port = envelope.sender().getPort();
                logger.debug("recvRespPunching: {}:{}", host, port);
                punchingPort = port;
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
        pingFuture = channel.eventLoop().scheduleAtFixedRate(() -> {
            writePing();
        }, 0, 5000, TimeUnit.MILLISECONDS);
        checkHeartFuture = channel.eventLoop().scheduleAtFixedRate(() -> {
            checkHeart();
        }, 0, 30 * 1000, TimeUnit.MILLISECONDS);
    }

    @Override
    public void close() {
        if (null != pingFuture) {
            pingFuture.cancel(true);
        }
        if (null != relayPunchingSchedule) {
            relayPunchingSchedule.cancel(true);
        }
        if (null != checkHeartFuture) {
            checkHeartFuture.cancel(true);
        }
        active = false;
    }

    private void checkHeart() {
        long now = System.currentTimeMillis();
        long diff = now - pingTime;
        if (diff >= 30000) {
            active = false;
        }
    }

    private void writePing() {
        Channel channel = punchingClient.getChannel();
        PunchingProtos.PunchingMessage message = PunchingProtos.PunchingMessage.newBuilder()
                .setType(PunchingProtos.MsgType.PingType)
                .setReqId(UUID.randomUUID().toString())
                .build();
        ByteBuf byteBuf = ProtosUtil.toBuffer(channel.alloc(), message);
        DatagramPacket packet = new DatagramPacket(byteBuf, new InetSocketAddress(punchingHost, punchingPort));
        logger.debug("ping: {}:{}", packet.recipient().getHostString(), packet.recipient().getPort());
        channel.writeAndFlush(packet);
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
                .setType(PunchingProtos.MsgType.ReqRelayPunchingType)
                .setReqId(UUID.randomUUID().toString())
                .setData(punchingData.toByteString())
                .build();
        ByteBuf byteBuf = ProtosUtil.toBuffer(channel.alloc(), message);
        DatagramPacket packet = new DatagramPacket(byteBuf, new InetSocketAddress(punchingClient.getServerHost(), punchingClient.getServerPort()));
        logger.debug("relayPunching: {}:{}", punchingClient.getServerHost(), punchingClient.getServerPort());
        channel.writeAndFlush(packet);
    }

    @Override
    public ChannelFuture writeAndFlush(Object data) {
        AddressedEnvelope envelope = new DefaultAddressedEnvelope(data, new InetSocketAddress(punchingHost, punchingPort));
        ChannelFuture future = punchingClient.writeAndFlush(envelope);
        return future;
    }
}
