package org.jaspercloud.punching.transport;

import java.util.Objects;

public class Node {

    private String nodeId;
    private String token;
    private String host;
    private int port;

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Node node = (Node) o;
        return getNodeId().equals(node.getNodeId()) && getToken().equals(node.getToken());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getNodeId(), getToken());
    }
}
