package org.jaspercloud.punching;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import org.jaspercloud.punching.proto.PunchingProtos;
import org.jaspercloud.punching.transport.Envelope;
import org.jaspercloud.punching.transport.PunchingClient;
import org.jaspercloud.punching.transport.PunchingConnection;
import org.jaspercloud.punching.transport.PunchingConnectionHandler;
import org.slf4j.impl.StaticLoggerBinder;

import java.nio.charset.StandardCharsets;

public class Client1Test {

    public static void main(String[] args) throws Exception {
        LoggerContext loggerContext = (LoggerContext) StaticLoggerBinder.getSingleton().getLoggerFactory();
        Logger root = loggerContext.getLogger("ROOT");
        root.setLevel(Level.INFO);
        Logger punching = loggerContext.getLogger("org.jaspercloud.punching");
        punching.setLevel(Level.DEBUG);
        PunchingClient punchingClient = new PunchingClient("127.0.0.1", 1080, 1001);
        punchingClient.setConnectionHandler(new PunchingConnectionHandler() {
            @Override
            public void onRead(PunchingConnection connection, byte[] data) {
                System.out.println("msg: " + new String(data));
                connection.writeAndFlush(("ack " + new String(data)).getBytes(StandardCharsets.UTF_8));
            }
        });
        punchingClient.afterPropertiesSet();
    }
}
