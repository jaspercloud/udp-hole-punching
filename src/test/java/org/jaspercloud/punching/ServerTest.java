package org.jaspercloud.punching;

import org.jaspercloud.punching.transport.PunchingServer;

public class ServerTest {

    public static void main(String[] args) throws Exception {
        PunchingServer punchingServer = new PunchingServer(1080);
        punchingServer.afterPropertiesSet();
    }
}
