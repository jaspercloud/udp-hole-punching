package org.jaspercloud.punching;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import io.netty.channel.Channel;
import org.jaspercloud.punching.transport.StreamChannel;
import org.jaspercloud.punching.transport.TunnelChannel;
import org.jaspercloud.punching.transport.UdpChannel;
import org.junit.jupiter.api.Test;
import org.slf4j.impl.StaticLoggerBinder;

import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;

public class UdpTest {

    @Test
    public void test() throws Exception {
        LoggerContext loggerContext = (LoggerContext) StaticLoggerBinder.getSingleton().getLoggerFactory();
        Logger root = loggerContext.getLogger("ROOT");
        root.setLevel(Level.INFO);
        Logger punching = loggerContext.getLogger("org.jaspercloud.punching");
        punching.setLevel(Level.DEBUG);

        Channel channel = UdpChannel.create(1002).sync().channel();
        TunnelChannel tunnelChannel = TunnelChannel.create(channel);
        tunnelChannel.connect(new InetSocketAddress("127.0.0.1", 1080));
//        StreamChannel serverChannel = StreamChannel.createServer(tunnelChannel);
        Channel streamChannel = StreamChannel.create(tunnelChannel).sync().channel();
        streamChannel.connect(new InetSocketAddress("127.0.0.1", 1001));
        System.out.println("connect success");
//        Thread.sleep(10 * 1000);
//        System.out.println("close success");
//        tunnelChannel.close();
        CountDownLatch countDownLatch = new CountDownLatch(1);
        countDownLatch.await();
    }
}
