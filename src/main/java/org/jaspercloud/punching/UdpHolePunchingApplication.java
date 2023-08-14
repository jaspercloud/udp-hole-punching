package org.jaspercloud.punching;

import org.jaspercloud.punching.transport.PunchingClient;
import org.jaspercloud.punching.transport.PunchingServer;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class UdpHolePunchingApplication {

    public static void main(String[] args) throws Exception {
//        PunchingServer punchingServer = new PunchingServer(1080);
//        punchingServer.afterPropertiesSet();
        PunchingClient punchingClient = new PunchingClient("47.122.65.163", 1080, 0);
        punchingClient.afterPropertiesSet();
        punchingClient.punching("61.174.208.54", 52839, 3000);
//        System.out.println("punching success");
        System.out.println();
    }

}
