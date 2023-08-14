package org.jaspercloud.punching.transport;

import io.netty.channel.AddressedEnvelope;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import org.jaspercloud.punching.proto.PunchingProtos;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public interface PunchingConnection {

    String getId();

    boolean isActive();

    void connect(long timeout) throws TimeoutException, ExecutionException, InterruptedException;

    void close();

    ChannelFuture writeAndFlush(Object data);

    void onChannelRead(ChannelHandlerContext ctx, AddressedEnvelope<PunchingProtos.PunchingMessage, InetSocketAddress> envelope) throws Exception;
}
