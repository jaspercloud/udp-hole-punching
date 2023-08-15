package org.jaspercloud.punching;

import com.google.protobuf.ByteString;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.jaspercloud.punching.proto.PunchingProtos;
import org.jaspercloud.punching.transport.*;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@SpringBootApplication
public class UdpHolePunchingApplication {

    public static void main(String[] args) throws Exception {
        new SpringApplicationBuilder(UdpHolePunchingApplication.class)
                .web(WebApplicationType.NONE).run(args);
//        startServer();
        startClient();
    }

    private static void startServer() throws Exception {
        PunchingServer punchingServer = new PunchingServer(1080);
        punchingServer.afterPropertiesSet();
    }

    private static void startClient() throws Exception {
//        "127.0.0.1", 1080
//        "47.122.65.163", 1080
        PunchingClient punchingClient = new PunchingClient("47.122.65.163", 1080, 0);
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
        PunchingConnection connection = punchingClient.createConnection("61.174.208.54", 63184, new PunchingConnectionHandler() {
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
