package org.jaspercloud.punching.transport;

import com.google.protobuf.ByteString;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import org.jaspercloud.punching.proto.PunchingProtos;

import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public class PunchingServerConnection implements PunchingConnection {

    private Channel channel;
    private String id;
    private InetSocketAddress localAddress;
    private InetSocketAddress remoteAddress;
    private volatile long pingTime = System.currentTimeMillis();

    public void setLocalAddress(InetSocketAddress localAddress) {
        this.localAddress = localAddress;
    }

    public void setRemoteAddress(InetSocketAddress remoteAddress) {
        this.remoteAddress = remoteAddress;
    }

    public void updateHeart() {
        pingTime = System.currentTimeMillis();
    }

    public PunchingServerConnection(Channel channel, String id) {
        this.channel = channel;
        this.id = id;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public boolean isActive() {
        throw new UnsupportedOperationException();
    }

    @Override
    public long getPingTime() {
        return pingTime;
    }

    @Override
    public InetSocketAddress localAddress() {
        return localAddress;
    }

    @Override
    public InetSocketAddress remoteAddress() {
        return remoteAddress;
    }

    @Override
    public void connect(long timeout) throws TimeoutException, ExecutionException, InterruptedException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void close() {

    }

    @Override
    public ChannelFuture writeAndFlush(byte[] data) {
        PunchingProtos.PunchingMessage message = PunchingProtos.PunchingMessage.newBuilder()
                .setChannelId(id)
                .setType(PunchingProtos.MsgType.Data)
                .setReqId(UUID.randomUUID().toString())
                .setData(ByteString.copyFrom(data))
                .build();
        Envelope envelope = Envelope.builder()
                .recipient(remoteAddress)
                .message(message)
                .build();
        ChannelFuture channelFuture = channel.writeAndFlush(envelope);
        return channelFuture;
    }
}
