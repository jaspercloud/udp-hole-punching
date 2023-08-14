package org.jaspercloud.punching.domain;

public class RelayPunchingData {

    private String host;
    private Integer port;

    public String getHost() {
        return host;
    }

    public Integer getPort() {
        return port;
    }

    public RelayPunchingData(String host, Integer port) {
        this.host = host;
        this.port = port;
    }
}
