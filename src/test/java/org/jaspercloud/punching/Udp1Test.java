package org.jaspercloud.punching;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import io.netty.channel.Channel;
import org.jaspercloud.punching.transport.StreamChannel;
import org.jaspercloud.punching.transport.TunnelChannel;
import org.jaspercloud.punching.transport.UdpChannel;
import org.slf4j.impl.StaticLoggerBinder;

import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;

public class Udp1Test {

    public static void main(String[] args) throws Exception {
        LoggerContext loggerContext = (LoggerContext) StaticLoggerBinder.getSingleton().getLoggerFactory();
        Logger root = loggerContext.getLogger("ROOT");
        root.setLevel(Level.INFO);
        Logger punching = loggerContext.getLogger("org.jaspercloud.punching");
        punching.setLevel(Level.DEBUG);
        Channel channel = UdpChannel.create(1001).sync().channel();
        TunnelChannel tunnelChannel = TunnelChannel.create(channel);
        tunnelChannel.connect(new InetSocketAddress("127.0.0.1", 1080));
        CountDownLatch countDownLatch = new CountDownLatch(1);
        countDownLatch.await();
    }
}