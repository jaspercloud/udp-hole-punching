package org.jaspercloud.punching;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import io.netty.channel.AddressedEnvelope;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.jaspercloud.punching.proto.PunchingProtos;
import org.jaspercloud.punching.transport.Envelope;
import org.jaspercloud.punching.transport.PunchingClient;
import org.slf4j.impl.StaticLoggerBinder;

import java.net.InetSocketAddress;

public class Client1Test {

    public static void main(String[] args) throws Exception {
        LoggerContext loggerContext = (LoggerContext) StaticLoggerBinder.getSingleton().getLoggerFactory();
        Logger root = loggerContext.getLogger("ROOT");
        root.setLevel(Level.INFO);
        Logger punching = loggerContext.getLogger("org.jaspercloud.punching");
        punching.setLevel(Level.DEBUG);
        PunchingClient punchingClient = new PunchingClient("127.0.0.1", 1080, 1001);
        punchingClient.setConnectionHandler(new SimpleChannelInboundHandler<Envelope<PunchingProtos.PunchingMessage>>() {
            @Override
            protected void channelRead0(ChannelHandlerContext ctx, Envelope<PunchingProtos.PunchingMessage> msg) throws Exception {
                PunchingProtos.PunchingMessage message = msg.message();
                switch (message.getType().getNumber()) {
                    case PunchingProtos.MsgType.Data_VALUE: {
                        System.out.println("msg: " + new String(message.getData().toByteArray()));
                        break;
                    }
                }
            }
        });
        punchingClient.afterPropertiesSet();
    }
}
