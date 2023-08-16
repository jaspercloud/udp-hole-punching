package org.jaspercloud.punching.transport;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;

import java.net.InetSocketAddress;

public class UdpChannel {

    public static Channel create(int localPort) throws InterruptedException {
        Channel channel = create("0.0.0.0", localPort);
        return channel;
    }

    public static Channel create(String localHost, int localPort) throws InterruptedException {
        InetSocketAddress local = new InetSocketAddress(localHost, localPort);
        NioEventLoopGroup group = new NioEventLoopGroup();
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group);
        bootstrap.channel(NioDatagramChannel.class);
        bootstrap.handler(new ChannelInitializer<DatagramChannel>() {
            @Override
            protected void initChannel(DatagramChannel ch) throws Exception {
                ChannelPipeline pipeline = ch.pipeline();
                pipeline.addLast("decoder", new Decoder());
                pipeline.addLast("encoder", new Encoder());
                pipeline.addLast("punching", TunnelChannel.createHandler());
            }
        });
        Channel channel = bootstrap.bind(local).sync().channel();
        channel.closeFuture().addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                group.shutdownGracefully();
            }
        });
        return channel;
    }
}
