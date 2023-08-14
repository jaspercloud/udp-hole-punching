package org.jaspercloud.punching;

import io.netty.channel.AddressedEnvelope;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.jaspercloud.punching.proto.PunchingProtos;
import org.jaspercloud.punching.transport.PunchingClient;
import org.jaspercloud.punching.transport.PunchingConnection;
import org.jaspercloud.punching.transport.PunchingConnectionHandler;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

import java.net.InetSocketAddress;
import java.util.UUID;

@SpringBootApplication
public class UdpHolePunchingApplication {

    public static void main(String[] args) throws Exception {
        new SpringApplicationBuilder(UdpHolePunchingApplication.class)
                .web(WebApplicationType.NONE).run(args);
//        PunchingServer punchingServer = new PunchingServer(1080);
//        punchingServer.afterPropertiesSet();
        PunchingClient punchingClient = new PunchingClient("47.122.65.163", 1080, 0);
        punchingClient.setConnectionHandler(new SimpleChannelInboundHandler<AddressedEnvelope<PunchingProtos.PunchingMessage, InetSocketAddress>>() {
            @Override
            protected void channelRead0(ChannelHandlerContext ctx, AddressedEnvelope<PunchingProtos.PunchingMessage, InetSocketAddress> msg) throws Exception {
                System.out.println("read: " + msg.content().getType().toString());
            }
        });
        punchingClient.afterPropertiesSet();
        PunchingConnection connection = punchingClient.createConnection("61.174.208.54", 52338, new PunchingConnectionHandler() {
            @Override
            public void onRead(PunchingConnection connection, AddressedEnvelope<PunchingProtos.PunchingMessage, InetSocketAddress> envelope) {
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
                    .build();
            connection.writeAndFlush(message);
            Thread.sleep(1000L);
        }
//        System.out.println();
    }

}
