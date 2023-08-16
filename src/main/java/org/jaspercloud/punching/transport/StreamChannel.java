package org.jaspercloud.punching.transport;

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

public class StreamChannel {

    private static Logger logger = LoggerFactory.getLogger(TunnelChannel.class);

    private TunnelChannel tunnelChannel;
    private String id;
    private String host;
    private int port;
    private boolean active;
    private ChannelPromise connectPromise;
    private ScheduledFuture<?> pingFuture;
    private ScheduledFuture<?> relayPunchingSchedule;

    public String getId() {
        return id;
    }

    public boolean isActive() {
        return active;
    }

    private StreamChannel(TunnelChannel tunnelChannel, String id, String host, int port) {
        this.tunnelChannel = tunnelChannel;
        this.id = id;
        this.host = host;
        this.port = port;
        tunnelChannel.addStreamChannel(this);
    }

    public static StreamChannel createServer(TunnelChannel tunnelChannel) {
        return null;
    }

    public static StreamChannel createClient(TunnelChannel channel, String host, int port) {
        return new StreamChannel(channel, UUID.randomUUID().toString(), host, port);
    }

    void receive(Envelope<PunchingProtos.PunchingMessage> message) {
        PunchingProtos.PunchingMessage request = message.message();
        InetSocketAddress sender = message.sender();
        switch (request.getType().getNumber()) {
            case PunchingProtos.MsgType.PongType_VALUE: {
                this.host = sender.getHostString();
                this.port = sender.getPort();
                logger.debug("recvPong: {}:{}", host, port);
                setConnected();
                break;
            }
            case PunchingProtos.MsgType.RespRelayPunchingType_VALUE: {
                String host = sender.getHostString();
                int port = sender.getPort();
                logger.debug("recvRespPunching: {}:{}", host, port);
                this.host = host;
                this.port = port;
                break;
            }
        }
    }

    private void setConnected() {
        if (null != connectPromise && !connectPromise.isDone()) {
            connectPromise.trySuccess();
            active = true;
        }
    }

    public void connect(long timeout) throws InterruptedException, ExecutionException, TimeoutException {
        connectPromise = tunnelChannel.newPromise();
        pingFuture = tunnelChannel.eventLoop().scheduleAtFixedRate(() -> {
            writePing();
        }, 0, 100, TimeUnit.MILLISECONDS);
        relayPunchingSchedule = tunnelChannel.eventLoop().scheduleAtFixedRate(() -> {
            writeRelayPunching();
        }, 0, 100, TimeUnit.MILLISECONDS);
        try {
            connectPromise.get(timeout, TimeUnit.MILLISECONDS);
        } catch (Throwable e) {
            close();
            throw e;
        } finally {
            pingFuture.cancel(true);
            relayPunchingSchedule.cancel(true);
        }
        pingFuture = tunnelChannel.eventLoop().scheduleAtFixedRate(() -> {
            writePing();
        }, 0, 5 * 1000, TimeUnit.MILLISECONDS);
    }

    private void close() {
        if (null != pingFuture) {
            pingFuture.cancel(true);
        }
        tunnelChannel.removeStreamChannel(this);
        active = false;
    }

    private void writePing() {
        PunchingProtos.PunchingMessage message = PunchingProtos.PunchingMessage.newBuilder()
                .setChannelId(tunnelChannel.getId())
                .setStreamId(id)
                .setType(PunchingProtos.MsgType.PingType)
                .setReqId(UUID.randomUUID().toString())
                .build();
        Envelope envelope = Envelope.builder()
                .recipient(new InetSocketAddress(host, port))
                .message(message)
                .build();
        logger.debug("sendPing: {}:{}", host, port);
        tunnelChannel.writeAndFlush(envelope);
    }

    private void writeRelayPunching() {
        PunchingProtos.ConnectionData connectionData = tunnelChannel.getConnectionData();
        PunchingProtos.PunchingData punchingData = PunchingProtos.PunchingData.newBuilder()
                .setPingHost(connectionData.getHost())
                .setPingPort(connectionData.getPort())
                .setPongHost(host)
                .setPongPort(port)
                .build();
        PunchingProtos.PunchingMessage message = PunchingProtos.PunchingMessage.newBuilder()
                .setChannelId(tunnelChannel.getId())
                .setStreamId(id)
                .setType(PunchingProtos.MsgType.ReqRelayPunchingType)
                .setReqId(UUID.randomUUID().toString())
                .setData(punchingData.toByteString())
                .build();
        logger.debug("relayPunching: {}:{}", host, port);
        tunnelChannel.writeRelayData(message);
    }

}
