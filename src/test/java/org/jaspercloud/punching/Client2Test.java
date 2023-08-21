//package org.jaspercloud.punching;
//
//import ch.qos.logback.classic.Level;
//import ch.qos.logback.classic.Logger;
//import ch.qos.logback.classic.LoggerContext;
//import com.google.protobuf.ByteString;
//import io.netty.channel.ChannelHandlerContext;
//import io.netty.channel.SimpleChannelInboundHandler;
//import org.jaspercloud.punching.proto.PunchingProtos;
//import org.jaspercloud.punching.transport.Envelope;
//import org.jaspercloud.punching.transport.PunchingClient;
//import org.jaspercloud.punching.transport.PunchingConnection;
//import org.jaspercloud.punching.transport.PunchingConnectionHandler;
//import org.slf4j.impl.StaticLoggerBinder;
//
//import java.nio.charset.StandardCharsets;
//import java.util.UUID;
//
//public class Client2Test {
//
//    public static void main(String[] args) throws Exception {
//        LoggerContext loggerContext = (LoggerContext) StaticLoggerBinder.getSingleton().getLoggerFactory();
//        Logger root = loggerContext.getLogger("ROOT");
//        root.setLevel(Level.INFO);
//        Logger punching = loggerContext.getLogger("org.jaspercloud.punching");
//        punching.setLevel(Level.DEBUG);
//        PunchingClient punchingClient = new PunchingClient("127.0.0.1", 1080, 1002);
//        punchingClient.setConnectionHandler(new PunchingConnectionHandler() {
//            @Override
//            public void onRead(PunchingConnection connection, byte[] data) {
//                System.out.println("onRead");
//            }
//        });
//        punchingClient.afterPropertiesSet();
//        PunchingConnection connection = punchingClient.createConnection("127.0.0.1", 1001, new PunchingConnectionHandler() {
//            @Override
//            public void onRead(PunchingConnection connection, byte[] data) {
//                System.out.println("msg: " + new String(data));
//            }
//        });
//        connection.connect(3000);
//        System.out.println("punching success");
//        while (true) {
//            boolean active = connection.isActive();
//            System.out.println("connectStatus: " + active);
//            connection.writeAndFlush("hello".getBytes(StandardCharsets.UTF_8));
//            Thread.sleep(1000L);
//        }
//    }
//}
