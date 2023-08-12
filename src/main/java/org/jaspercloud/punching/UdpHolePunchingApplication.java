package org.jaspercloud.punching;

import org.jaspercloud.punching.transport.PunchingServer;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class UdpHolePunchingApplication {

    public static void main(String[] args) throws Exception {
        PunchingServer punchingServer = new PunchingServer(1080);
        punchingServer.afterPropertiesSet();
//        PunchingClient punchingClient1 = new PunchingClient("localhost", 1080, 1001);
//        punchingClient1.afterPropertiesSet();
//        PunchingClient punchingClient2 = new PunchingClient("localhost", 1080, 1002);
//        punchingClient2.afterPropertiesSet();
//        punchingClient1.punching("localhost", 1002, 3000);
//        System.out.println();
    }

}
