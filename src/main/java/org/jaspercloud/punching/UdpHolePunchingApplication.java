package org.jaspercloud.punching;

import org.jaspercloud.punching.transport.PunchingClient;
import org.jaspercloud.punching.transport.PunchingConnection;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootApplication
public class UdpHolePunchingApplication {

    public static void main(String[] args) throws Exception {
        new SpringApplicationBuilder(UdpHolePunchingApplication.class).web(WebApplicationType.NONE).run(args);
//        PunchingServer punchingServer = new PunchingServer(1080);
//        punchingServer.afterPropertiesSet();
        PunchingClient punchingClient = new PunchingClient("47.122.65.163", 1080, 0);
        punchingClient.afterPropertiesSet();
        PunchingConnection connection = punchingClient.createConnection("61.174.208.54", 50896);
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
