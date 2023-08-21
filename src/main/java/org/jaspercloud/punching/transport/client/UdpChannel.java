package org.jaspercloud.punching.transport.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import org.jaspercloud.punching.transport.Decoder;
import org.jaspercloud.punching.transport.Encoder;

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
        bootstrap.handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ChannelPipeline pipeline = ch.pipeline();
                pipeline.addLast("decoder", new Decoder());
                pipeline.addLast("encoder", new Encoder());
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
