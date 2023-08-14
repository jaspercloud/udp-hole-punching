package org.jaspercloud.punching.transport;

import io.netty.buffer.ByteBuf;
import io.netty.channel.AddressedEnvelope;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.ReferenceCountUtil;
import org.jaspercloud.punching.proto.PunchingProtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

public class ClientHandler extends ChannelInboundHandlerAdapter {

    private Logger logger = LoggerFactory.getLogger(getClass());

    private ConnectionManager connectionManager;

    public ClientHandler(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        try {
            DatagramPacket packet = (DatagramPacket) msg;
            InetSocketAddress sender = packet.sender();
            PunchingProtos.PunchingMessage request = ProtosUtil.toProto(packet.content());
            switch (request.getType().getNumber()) {
                case PunchingProtos.MsgType.PingType_VALUE: {
                    logger.debug("recvPing: {}:{}", sender.getHostString(), sender.getPort());
                    PunchingProtos.PunchingMessage message = PunchingProtos.PunchingMessage.newBuilder()
                            .setType(PunchingProtos.MsgType.PongType)
                            .setReqId(request.getReqId())
                            .build();
                    ByteBuf byteBuf = ProtosUtil.toBuffer(ctx.alloc(), message);
                    DatagramPacket data = new DatagramPacket(byteBuf, sender);
                    ctx.writeAndFlush(data);
                    break;
                }
                case PunchingProtos.MsgType.ReqRelayPunchingType_VALUE: {
                    PunchingProtos.PunchingData punchingData = PunchingProtos.PunchingData.parseFrom(request.getData());
                    logger.debug("recvReqPunching: {}:{} -> {}:{}",
                            punchingData.getPingHost(), punchingData.getPingPort(),
                            punchingData.getPongHost(), punchingData.getPongPort());
                    PunchingProtos.PunchingMessage message = PunchingProtos.PunchingMessage.newBuilder()
                            .setType(PunchingProtos.MsgType.RespRelayPunchingType)
                            .setReqId(request.getReqId())
                            .build();
                    ByteBuf byteBuf = ProtosUtil.toBuffer(ctx.alloc(), message);
                    DatagramPacket data = new DatagramPacket(byteBuf, new InetSocketAddress(punchingData.getPingHost(), punchingData.getPingPort()));
                    ctx.writeAndFlush(data);
                    break;
                }
                default: {
                    connectionManager.channelRead(ctx, sender, request);
                    AddressedEnvelope envelope = new AddressedEnvelopeBuilder()
                            .sender(sender)
                            .message(request)
                            .build();
                    super.channelRead(ctx, envelope);
                    break;
                }
            }
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }
}
