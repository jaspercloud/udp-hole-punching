package org.jaspercloud.punching.transport;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
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
            Envelope<PunchingProtos.PunchingMessage> envelope = (Envelope<PunchingProtos.PunchingMessage>) msg;
            InetSocketAddress sender = envelope.sender();
            InetSocketAddress recipient = envelope.recipient();
            PunchingProtos.PunchingMessage request = envelope.message();
            switch (request.getType().getNumber()) {
                case PunchingProtos.MsgType.PingType_VALUE: {
                    logger.debug("recvPing: {}:{}", sender.getHostString(), sender.getPort());
                    PunchingProtos.HeartData heartData = PunchingProtos.HeartData.parseFrom(request.getData());
                    PunchingRemoteConnection connection = new PunchingRemoteConnection(ctx.channel(), heartData.getChannelId());
                    connection.setLocalAddress(recipient);
                    connection.setRemoteAddress(sender);
                    boolean add = connectionManager.addConnection(connection);
                    PunchingProtos.PunchingMessage message = PunchingProtos.PunchingMessage.newBuilder()
                            .setType(PunchingProtos.MsgType.PongType)
                            .setReqId(request.getReqId())
                            .build();
                    Envelope data = Envelope.builder()
                            .recipient(sender)
                            .message(message)
                            .build();
                    ctx.writeAndFlush(data);
                    if (!add) {
                        connection = (PunchingRemoteConnection) connectionManager.getConnection(heartData.getChannelId());
                        connection.updateHeart();
                    }
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
                    InetSocketAddress address = new InetSocketAddress(punchingData.getPingHost(), punchingData.getPingPort());
                    Envelope data = Envelope.builder()
                            .recipient(address)
                            .message(message)
                            .build();
                    ctx.writeAndFlush(data);
                    break;
                }
                default: {
                    boolean read = connectionManager.channelRead(ctx, envelope);
                    if (!read) {
                        super.channelRead(ctx, envelope);
                    }
                    break;
                }
            }
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }
}
