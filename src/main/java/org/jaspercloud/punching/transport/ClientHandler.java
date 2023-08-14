package org.jaspercloud.punching.transport;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.socket.DatagramPacket;
import org.jaspercloud.punching.proto.PunchingProtos;

import java.net.InetSocketAddress;

public class ClientHandler extends ChannelInboundHandlerAdapter {

    private NodeManager nodeManager;

    public ClientHandler(NodeManager nodeManager) {
        this.nodeManager = nodeManager;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("channelActive");
        super.channelActive(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        DatagramPacket packet = (DatagramPacket) msg;
        PunchingProtos.PunchingMessage request = ProtosUtil.toProto(packet.content());
        System.out.println(String.format("recvType: %s", request.getType().toString()));
        switch (request.getType().getNumber()) {
            case PunchingProtos.MsgType.PingType_VALUE: {
                System.out.println(String.format("recvPing: %s:%d", packet.sender().getHostString(), packet.sender().getPort()));
                PunchingProtos.PunchingMessage message = PunchingProtos.PunchingMessage.newBuilder()
                        .setType(PunchingProtos.MsgType.PongType)
                        .setReqId(request.getReqId())
                        .build();
                ByteBuf byteBuf = ProtosUtil.toBuffer(ctx.alloc(), message);
                DatagramPacket data = new DatagramPacket(byteBuf, packet.sender());
                ctx.writeAndFlush(data);
                break;
            }
            case PunchingProtos.MsgType.PongType_VALUE: {
                String host = packet.sender().getHostString();
                int port = packet.sender().getPort();
                System.out.println(String.format("recvPong: %s:%d", host, port));
                nodeManager.updatePong(ctx.channel(), host, port);
                break;
            }
            case PunchingProtos.MsgType.ReqRelayPunchingType_VALUE: {
                PunchingProtos.PunchingData punchingData = PunchingProtos.PunchingData.parseFrom(request.getData());
                System.out.println(String.format("recvReqPunching: %s:%d -> %s:%d",
                        punchingData.getPingHost(), punchingData.getPingPort(),
                        punchingData.getPongHost(), punchingData.getPongPort()));
                PunchingProtos.PunchingMessage message = PunchingProtos.PunchingMessage.newBuilder()
                        .setType(PunchingProtos.MsgType.RespRelayPunchingType)
                        .setReqId(request.getReqId())
                        .build();
                ByteBuf byteBuf = ProtosUtil.toBuffer(ctx.alloc(), message);
                DatagramPacket data = new DatagramPacket(byteBuf, new InetSocketAddress(punchingData.getPingHost(), punchingData.getPingPort()));
                ctx.writeAndFlush(data);
                break;
            }
            case PunchingProtos.MsgType.RespRelayPunchingType_VALUE: {
                String host = packet.sender().getHostString();
                int port = packet.sender().getPort();
                System.out.println(String.format("recvRespPunching: %s:%d", host, port));
                nodeManager.updateNodePort(ctx.channel(), host, port);
                break;
            }
        }
    }
}
