package org.jaspercloud.punching;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

public class TcpTest {

    @Test
    public void test() throws Exception {
        NioEventLoopGroup group = new NioEventLoopGroup();
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group);
        bootstrap.channel(NioSocketChannel.class);
        bootstrap.handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ChannelPipeline pipeline = ch.pipeline();
                System.out.println("initChannel");
            }
        });
        Channel channel1 = bootstrap.connect(new InetSocketAddress("192.222.8.159", 18083)).sync().channel();
        boolean active1 = channel1.isActive();
        Channel channel2 = bootstrap.connect(new InetSocketAddress("192.222.8.159", 18083)).sync().channel();
        boolean active2 = channel1.isActive();
        System.out.println();
    }
}
