package org.jaspercloud.punching;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import com.google.protobuf.ByteString;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.jaspercloud.punching.proto.PunchingProtos;
import org.jaspercloud.punching.transport.Envelope;
import org.jaspercloud.punching.transport.PunchingClient;
import org.jaspercloud.punching.transport.PunchingConnection;
import org.jaspercloud.punching.transport.PunchingConnectionHandler;
import org.slf4j.impl.StaticLoggerBinder;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class Client2Test {

    public static void main(String[] args) throws Exception {
        LoggerContext loggerContext = (LoggerContext) StaticLoggerBinder.getSingleton().getLoggerFactory();
        Logger root = loggerContext.getLogger("ROOT");
        root.setLevel(Level.INFO);
        Logger punching = loggerContext.getLogger("org.jaspercloud.punching");
        punching.setLevel(Level.DEBUG);
        PunchingClient punchingClient = new PunchingClient("127.0.0.1", 1080, 1002);
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
        PunchingConnection connection = punchingClient.createConnection("127.0.0.1", 1001, new PunchingConnectionHandler() {
            @Override
            public void onRead(PunchingConnection connection, Envelope<PunchingProtos.PunchingMessage> envelope) {
                System.out.println("onRead");
            }
        });
        connection.connect(3000);
        System.out.println("punching success");
        while (true) {
            boolean active = connection.isActive();
            System.out.println("connectStatus: " + active);
            PunchingProtos.PunchingMessage message = PunchingProtos.PunchingMessage.newBuilder()
                    .setType(PunchingProtos.MsgType.Data)
                    .setReqId(UUID.randomUUID().toString())
                    .setData(ByteString.copyFrom("hello".getBytes(StandardCharsets.UTF_8)))
                    .build();
            connection.writeAndFlush(message);
            Thread.sleep(1000L);
        }
    }
}
