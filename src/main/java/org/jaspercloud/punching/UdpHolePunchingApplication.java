package org.jaspercloud.punching;

import org.jaspercloud.punching.transport.PunchingClient;
import org.jaspercloud.punching.transport.PunchingConnection;
import org.jaspercloud.punching.transport.PunchingConnectionHandler;
import org.jaspercloud.punching.transport.PunchingServer;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

import java.nio.charset.StandardCharsets;

@SpringBootApplication
public class UdpHolePunchingApplication {

    public static void main(String[] args) throws Exception {
        new SpringApplicationBuilder(UdpHolePunchingApplication.class)
                .web(WebApplicationType.NONE).run(args);
        startServer();
//        startClient();
    }

    private static void startServer() throws Exception {
        PunchingServer punchingServer = new PunchingServer(1080);
        punchingServer.afterPropertiesSet();
    }

    private static void startClient() throws Exception {
//        "127.0.0.1", 1080
//        "47.122.65.163", 1080
        PunchingClient punchingClient = new PunchingClient("47.122.65.163", 1080, 0);
        punchingClient.setConnectionHandler(new PunchingConnectionHandler() {
            @Override
            public void onRead(PunchingConnection connection, byte[] data) {
                System.out.println("msg: " + new String(data));
                connection.writeAndFlush(("ack " + new String(data)).getBytes(StandardCharsets.UTF_8));
            }
        });
        punchingClient.afterPropertiesSet();
        PunchingConnection connection = punchingClient.createConnection("61.174.208.54", 57760, new PunchingConnectionHandler() {
            @Override
            public void onRead(PunchingConnection connection, byte[] data) {
                System.out.println("msg: " + new String(data));
            }
        });
        connection.connect(3000);
        System.out.println("punching success");
        while (true) {
            boolean active = connection.isActive();
            System.out.println("connectStatus: " + active);
            connection.writeAndFlush("hello".getBytes(StandardCharsets.UTF_8));
            Thread.sleep(1000L);
        }
    }

}
