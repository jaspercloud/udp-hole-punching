//package org.jaspercloud.punching.transport;
//
//import io.netty.channel.ChannelFuture;
//
//import java.net.InetSocketAddress;
//import java.util.concurrent.ExecutionException;
//import java.util.concurrent.TimeoutException;
//
//public interface PunchingConnection {
//
//    String getId();
//
//    boolean isActive();
//
//    InetSocketAddress localAddress();
//
//    InetSocketAddress remoteAddress();
//
//    long getPingTime();
//
//    void connect(long timeout) throws TimeoutException, ExecutionException, InterruptedException;
//
//    void close();
//
//    ChannelFuture writeAndFlush(byte[] data);
//}
