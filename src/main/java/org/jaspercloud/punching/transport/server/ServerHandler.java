package org.jaspercloud.punching.transport.server;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.apache.commons.lang3.StringUtils;
import org.jaspercloud.punching.proto.PunchingProtos;
import org.jaspercloud.punching.transport.Envelope;
import org.jaspercloud.punching.transport.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ServerHandler extends SimpleChannelInboundHandler<Envelope<PunchingProtos.PunchingMessage>> {

    private Logger logger = LoggerFactory.getLogger(getClass());

    private Map<String, Node> nodeMap = new ConcurrentHashMap<>();

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Envelope<PunchingProtos.PunchingMessage> msg) throws Exception {
        PunchingProtos.PunchingMessage request = msg.message();
        switch (request.getType().getNumber()) {
            case PunchingProtos.MsgType.ReqRegisterType_VALUE: {
                processRegister(ctx, msg);
                break;
            }
            case PunchingProtos.MsgType.ReqRelayPunchingType_VALUE: {
                processRelayPunching(ctx, msg);
                break;
            }
            case PunchingProtos.MsgType.ReqQueryNode_VALUE: {
                processQueryNode(ctx, msg);
                break;
            }
        }
    }

    private void processQueryNode(ChannelHandlerContext ctx, Envelope<PunchingProtos.PunchingMessage> msg) throws Exception {
        PunchingProtos.PunchingMessage request = msg.message();
        InetSocketAddress sender = msg.sender();
        PunchingProtos.NodeData nodeData = PunchingProtos.NodeData.parseFrom(request.getData());
        String key = StringUtils.join(nodeData.getNodeId(), nodeData.getToken(), "-");
        Node node = nodeMap.get(key);
        nodeData = nodeData.toBuilder()
                .setHost(node.getHost())
                .setPort(node.getPort())
                .build();
        PunchingProtos.PunchingMessage message = PunchingProtos.PunchingMessage.newBuilder()
                .setChannelId(request.getChannelId())
                .setType(PunchingProtos.MsgType.RespQueryNode)
                .setReqId(request.getReqId())
                .setData(nodeData.toByteString())
                .build();
        Envelope data = Envelope.builder()
                .recipient(sender)
                .message(message)
                .build();
        ctx.writeAndFlush(data);
    }

    private void processRegister(ChannelHandlerContext ctx, Envelope<PunchingProtos.PunchingMessage> envelope) throws Exception {
        PunchingProtos.PunchingMessage request = envelope.message();
        InetSocketAddress sender = envelope.sender();
        String host = sender.getHostString();
        int port = sender.getPort();
        PunchingProtos.NodeData nodeData = PunchingProtos.NodeData.parseFrom(request.getData());
        logger.debug("recvRegister: addr={}:{}, nodeId={}, token={}", host, port, nodeData.getNodeId(), nodeData.getToken());
        registerNode(nodeData, host, port);
        PunchingProtos.PunchingMessage message = PunchingProtos.PunchingMessage.newBuilder()
                .setChannelId(request.getChannelId())
                .setType(PunchingProtos.MsgType.RespRegisterType)
                .setReqId(request.getReqId())
                .setData(PunchingProtos.ConnectionData.newBuilder()
                        .setHost(host)
                        .setPort(port)
                        .build().toByteString())
                .build();
        Envelope data = Envelope.builder()
                .recipient(sender)
                .message(message)
                .build();
        ctx.writeAndFlush(data);
    }

    private void processRelayPunching(ChannelHandlerContext ctx, Envelope<PunchingProtos.PunchingMessage> envelope) throws Exception {
        PunchingProtos.PunchingMessage message = envelope.message();
        PunchingProtos.PunchingData punchingData = PunchingProtos.PunchingData.parseFrom(message.getData());
        logger.debug("recvRelayPunching: {}:{} -> {}:{}",
                punchingData.getPingHost(), punchingData.getPingPort(),
                punchingData.getPongHost(), punchingData.getPongPort());
        Envelope data = Envelope.builder()
                .recipient(new InetSocketAddress(punchingData.getPongHost(), punchingData.getPongPort()))
                .message(message)
                .build();
        ctx.writeAndFlush(data);
    }

    private void registerNode(PunchingProtos.NodeData nodeData, String host, int port) {
        String key = StringUtils.join(nodeData.getNodeId(), nodeData.getToken(), "-");
        Node node = new Node();
        node.setNodeId(nodeData.getNodeId());
        node.setToken(nodeData.getToken());
        node.setHost(host);
        node.setPort(port);
        nodeMap.put(key, node);
    }
}
