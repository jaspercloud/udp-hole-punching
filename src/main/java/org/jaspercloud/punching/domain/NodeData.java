package org.jaspercloud.punching.domain;

import io.netty.util.concurrent.ScheduledFuture;

import java.util.concurrent.CompletableFuture;

public class NodeData {

    private String nodeHost;
    private int nodePort;
    private CompletableFuture future = new CompletableFuture();
    private ScheduledFuture<?> pingFuture;

    public String getNodeHost() {
        return nodeHost;
    }

    public void setNodeHost(String nodeHost) {
        this.nodeHost = nodeHost;
    }

    public int getNodePort() {
        return nodePort;
    }

    public void setNodePort(int nodePort) {
        this.nodePort = nodePort;
    }

    public CompletableFuture getFuture() {
        return future;
    }

    public ScheduledFuture<?> getPingFuture() {
        return pingFuture;
    }

    public void setPingFuture(ScheduledFuture<?> pingFuture) {
        this.pingFuture = pingFuture;
    }

    public NodeData() {
    }
}
