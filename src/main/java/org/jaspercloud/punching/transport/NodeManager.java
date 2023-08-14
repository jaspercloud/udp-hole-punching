package org.jaspercloud.punching.transport;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.concurrent.ScheduledFuture;
import org.jaspercloud.punching.domain.NodeData;
import org.jaspercloud.punching.proto.PunchingProtos;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class NodeManager {

    private Map<String, NodeData> nodeMap = new ConcurrentHashMap<>();

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
        nodeData.getFuture().complete(true);
    }

    public NodeData addNode(Channel channel, String nodeHost, int nodePort) {
        NodeData nodeData = nodeMap.get(nodeHost);
        if (null != nodeData) {
            nodeData.getPingFuture().cancel(true);
        }
        nodeData = new NodeData();
        nodeData.setNodeHost(nodeHost);
        nodeData.setNodePort(nodePort);
        final NodeData node = nodeData;
        ScheduledFuture<?> pingFuture = channel.eventLoop().scheduleAtFixedRate(() -> {
            PunchingProtos.PunchingMessage message = PunchingProtos.PunchingMessage.newBuilder()
                    .setType(PunchingProtos.MsgType.PingType)
                    .setReqId(UUID.randomUUID().toString())
                    .build();
            ByteBuf byteBuf = ProtosUtil.toBuffer(channel.alloc(), message);
            DatagramPacket packet = new DatagramPacket(byteBuf, new InetSocketAddress(nodeHost, node.getNodePort()));
            System.out.println(String.format("ping: %s:%d", packet.recipient().getHostString(), packet.recipient().getPort()));
            channel.writeAndFlush(packet);
        }, 0, 1000, TimeUnit.MILLISECONDS);
        nodeData.setPingFuture(pingFuture);
        nodeMap.put(nodeHost, nodeData);
        return nodeData;
    }
}
