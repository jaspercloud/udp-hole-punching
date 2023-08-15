package org.jaspercloud.punching.transport;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import org.jaspercloud.punching.proto.PunchingProtos;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public interface PunchingConnection {

    String getId();

    boolean isActive();

    void connect(long timeout) throws TimeoutException, ExecutionException, InterruptedException;

    void close();

    ChannelFuture writeAndFlush(PunchingProtos.PunchingMessage data);

    void onChannelRead(ChannelHandlerContext ctx, Envelope<PunchingProtos.PunchingMessage> envelope) throws Exception;
}
