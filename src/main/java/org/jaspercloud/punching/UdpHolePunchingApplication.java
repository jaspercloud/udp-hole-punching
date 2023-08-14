package org.jaspercloud.punching;

import io.netty.channel.AddressedEnvelope;
import org.jaspercloud.punching.proto.PunchingProtos;
import org.jaspercloud.punching.transport.PunchingClient;
import org.jaspercloud.punching.transport.PunchingConnection;
import org.jaspercloud.punching.transport.PunchingConnectionHandler;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

import java.net.InetSocketAddress;

@SpringBootApplication
public class UdpHolePunchingApplication {

    public static void main(String[] args) throws Exception {
        new SpringApplicationBuilder(UdpHolePunchingApplication.class)
                .web(WebApplicationType.NONE).run(args);
//        PunchingServer punchingServer = new PunchingServer(1080);
//        punchingServer.afterPropertiesSet();
        PunchingClient punchingClient = new PunchingClient("47.122.65.163", 1080, 0);
        punchingClient.afterPropertiesSet();
        PunchingConnection connection = punchingClient.createConnection("61.174.208.54", 52338, new PunchingConnectionHandler() {
            @Override
            public void onActive(PunchingConnection connection) {
                System.out.println("onActive");
            }

            @Override
            public void onRead(PunchingConnection connection, AddressedEnvelope<PunchingProtos.PunchingMessage, InetSocketAddress> envelope) {
                System.out.println("onRead");
            }

            @Override
            public void onInActive(PunchingConnection connection) {
                System.out.println("onInActive");
            }
        });
        connection.connect(3000);
        System.out.println("punching success");
        while (true) {
            boolean active = connection.isActive();
            System.out.println("connectStatus: " + active);
            Thread.sleep(1000L);
        }
//        System.out.println();
    }

}
