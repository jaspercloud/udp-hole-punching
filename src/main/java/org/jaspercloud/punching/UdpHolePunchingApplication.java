package org.jaspercloud.punching;

import org.jaspercloud.punching.transport.PunchingServer;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootApplication
public class UdpHolePunchingApplication {

    public static void main(String[] args) throws Exception {
        new SpringApplicationBuilder(UdpHolePunchingApplication.class)
                .web(WebApplicationType.NONE).run(args);
        startServer();
    }

    private static void startServer() throws Exception {
        PunchingServer punchingServer = new PunchingServer(1080);
        punchingServer.afterPropertiesSet();
    }
}
