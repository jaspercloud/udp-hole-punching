package org.jaspercloud.punching.transport;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.socket.DatagramPacket;
import org.jaspercloud.punching.domain.RelayPunchingData;
import org.jaspercloud.punching.proto.PunchingProtos;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class ClientHandler extends ChannelInboundHandlerAdapter {

    private Map<String, CompletableFuture> futureMap;

    public ClientHandler(Map<String, CompletableFuture> futureMap) {
        this.futureMap = futureMap;
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
                System.out.println(String.format("recvPong: %s:%d", packet.sender().getHostString(), packet.sender().getPort()));
                CompletableFuture future = futureMap.get(request.getReqId());
                if (null != future) {
                    future.complete(new RelayPunchingData(packet.sender().getHostString(), packet.sender().getPort()));
                }
                break;
            }
            case PunchingProtos.MsgType.RelayPunchingType_VALUE: {
                PunchingProtos.PunchingData punchingData = PunchingProtos.PunchingData.parseFrom(request.getData());
                System.out.println(String.format("recvPunching: %s:%d -> %s:%d",
                        punchingData.getPingHost(), punchingData.getPingPort(),
                        punchingData.getPongHost(), punchingData.getPongPort()));
                PunchingProtos.PunchingMessage message = PunchingProtos.PunchingMessage.newBuilder()
                        .setType(PunchingProtos.MsgType.PongType)
                        .setReqId(request.getReqId())
                        .build();
                ByteBuf byteBuf = ProtosUtil.toBuffer(ctx.alloc(), message);
                DatagramPacket data = new DatagramPacket(byteBuf, new InetSocketAddress(punchingData.getPingHost(), punchingData.getPingPort()));
                ctx.writeAndFlush(data);
                break;
            }
        }
    }
}
