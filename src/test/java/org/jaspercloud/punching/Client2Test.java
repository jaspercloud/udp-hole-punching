package org.jaspercloud.punching;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import org.jaspercloud.punching.proto.PunchingProtos;
import org.jaspercloud.punching.transport.client.*;
import org.slf4j.impl.StaticLoggerBinder;

import java.net.InetSocketAddress;
import java.util.Objects;

public class Client2Test {

    public static void main(String[] args) throws Exception {
        LoggerContext loggerContext = (LoggerContext) StaticLoggerBinder.getSingleton().getLoggerFactory();
        Logger root = loggerContext.getLogger("ROOT");
        root.setLevel(Level.INFO);
        Logger punching = loggerContext.getLogger("org.jaspercloud.punching");
        punching.setLevel(Level.DEBUG);

        StreamChannelManager streamChannelManager = new StreamChannelManager(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {

            }
        });
        TunnelChannelManager tunnelChannelManager = new TunnelChannelManager(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ch.pipeline().addLast(streamChannelManager);
            }
        });

        Channel channel = UdpChannel.create(0);
        channel.pipeline().addLast(tunnelChannelManager);

        TunnelChannel tunnelChannel = TunnelChannel.createNode(channel, "test2", "test");
        tunnelChannelManager.addTunnelChannel(tunnelChannel);
        tunnelChannel.connect(new InetSocketAddress("47.122.65.163", 1080)).sync();
        tunnelChannel.pipeline().addLast(streamChannelManager);

        PunchingProtos.NodeData nodeData = tunnelChannel.queryNode("test1", "test", 3000);
        System.out.println(String.format("nodeData: %s:%s", nodeData.getHost(), nodeData.getPort()));

        StreamChannel streamChannel = StreamChannel.createClient(tunnelChannel);
        streamChannelManager.addStreamChannel(streamChannel);
        streamChannel.connect(new InetSocketAddress(nodeData.getHost(), nodeData.getPort())).sync();
        streamChannel.pipeline().addLast(new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                Channel channel = ctx.channel();
                byte[] bytes = (byte[]) msg;
                String text = new String(bytes);
                System.out.println(String.format("%s->%s: %s", channel.remoteAddress(), channel.localAddress(), text));
            }
        });

        int port = nodeData.getPort();
        while (true) {
            nodeData = tunnelChannel.queryNode("test1", "test", 3000);
            System.out.println(String.format("nodeData: %s:%s", nodeData.getHost(), nodeData.getPort()));
            if (port != nodeData.getPort()) {
                port = nodeData.getPort();
                streamChannel.close().sync();
                System.out.println("init");
                streamChannel = StreamChannel.createClient(tunnelChannel);
                streamChannelManager.addStreamChannel(streamChannel);
                streamChannel.connect(new InetSocketAddress(nodeData.getHost(), nodeData.getPort())).sync();
                streamChannel.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                        Channel channel = ctx.channel();
                        byte[] bytes = (byte[]) msg;
                        String text = new String(bytes);
                        System.out.println(String.format("%s->%s: %s", channel.remoteAddress(), channel.localAddress(), text));
                    }
                });
            } else {
                streamChannel.writeAndFlush("say hello");
            }
            Thread.sleep(1000L);
        }
    }
}
