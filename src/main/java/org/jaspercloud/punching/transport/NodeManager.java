package org.jaspercloud.punching.transport;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.concurrent.ScheduledFuture;
import org.jaspercloud.punching.domain.NodeData;
import org.jaspercloud.punching.proto.PunchingProtos;
import org.springframework.beans.factory.InitializingBean;

import java.net.InetSocketAddress;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class NodeManager implements InitializingBean {

    private Map<String, NodeData> nodeMap = new ConcurrentHashMap<>();

    @Override
    public void afterPropertiesSet() throws Exception {
        new Thread(() -> {
            while (true) {
                try {
                    Iterator<Map.Entry<String, NodeData>> iterator = nodeMap.entrySet().iterator();
                    while (iterator.hasNext()) {
                        Map.Entry<String, NodeData> next = iterator.next();
                        long now = System.currentTimeMillis();
                        Long lastPingTime = next.getValue().getPingTime();
                        long diff = now - lastPingTime;
                        if (diff > (30 * 1000L)) {
                            System.out.println("channel timeout");
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                try {
                    Thread.sleep(30 * 1000L);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public void updateNodePort(Channel channel, String nodeHost, int nodePort) {
        NodeData nodeData = nodeMap.get(nodeHost);
        if (null == nodeData) {
            return;
        }
        nodeData.setNodeHost(nodeHost);
        nodeData.setNodePort(nodePort);
    }

    public void updatePong(Channel channel, String nodeHost, int nodePort) {
        NodeData nodeData = nodeMap.get(nodeHost);
        if (null == nodeData) {
            return;
        }
        CompletableFuture future = nodeData.getFuture();
        if (!future.isDone()) {
            nodeData.getPingFuture().cancel(true);
            nodeData.setPingFuture(createPingFuture(channel, nodeData, 5000));
        }
        future.complete(true);
        nodeData.setPingTime(System.currentTimeMillis());
    }

    public NodeData addNode(Channel channel, String nodeHost, int nodePort) {
        NodeData nodeData = nodeMap.get(nodeHost);
        if (null != nodeData) {
            nodeData.getPingFuture().cancel(true);
        }
        nodeData = new NodeData();
        nodeData.setNodeHost(nodeHost);
        nodeData.setNodePort(nodePort);
        nodeData.setPingFuture(createPingFuture(channel, nodeData, 100));
        nodeMap.put(nodeHost, nodeData);
        return nodeData;
    }

    private ScheduledFuture<?> createPingFuture(Channel channel, NodeData nodeData, long period) {
        ScheduledFuture<?> pingFuture = channel.eventLoop().scheduleAtFixedRate(() -> {
            PunchingProtos.PunchingMessage message = PunchingProtos.PunchingMessage.newBuilder()
                    .setType(PunchingProtos.MsgType.PingType)
                    .setReqId(UUID.randomUUID().toString())
                    .build();
            ByteBuf byteBuf = ProtosUtil.toBuffer(channel.alloc(), message);
            DatagramPacket packet = new DatagramPacket(byteBuf, new InetSocketAddress(nodeData.getNodeHost(), nodeData.getNodePort()));
            System.out.println(String.format("ping: %s:%d", packet.recipient().getHostString(), packet.recipient().getPort()));
            channel.writeAndFlush(packet);
        }, 0, period, TimeUnit.MILLISECONDS);
        return pingFuture;
    }
}
