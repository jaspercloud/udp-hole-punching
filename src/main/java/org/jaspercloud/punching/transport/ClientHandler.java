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
            logger.info("recvType: type={}, channel={}:{}", request.getType().toString(), sender.getHostString(), sender.getPort());
            switch (request.getType().getNumber()) {
                case PunchingProtos.MsgType.PingType_VALUE: {
                    logger.debug("recvPing: {}:{}", sender.getHostString(), sender.getPort());
                    PunchingServerConnection connection = new PunchingServerConnection(ctx.channel(), request.getChannelId());
                    connection.setLocalAddress(recipient);
                    connection.setRemoteAddress(sender);
                    boolean add = connectionManager.addConnection(connection);
                    PunchingProtos.PunchingMessage message = PunchingProtos.PunchingMessage.newBuilder()
                            .setChannelId(request.getChannelId())
                            .setType(PunchingProtos.MsgType.PongType)
                            .setReqId(request.getReqId())
                            .build();
                    Envelope data = Envelope.builder()
                            .recipient(sender)
                            .message(message)
                            .build();
                    ctx.writeAndFlush(data);
                    if (!add) {
                        connection = (PunchingServerConnection) connectionManager.getConnection(request.getChannelId());
                        connection.updateHeart();
                    }
                    break;
                }
                case PunchingProtos.MsgType.PongType_VALUE: {
                    String host = sender.getHostString();
                    int port = sender.getPort();
                    logger.debug("recvPong: {}:{}", host, port);
                    PunchingClientConnection connection = (PunchingClientConnection) connectionManager.getConnection(request.getChannelId());
                    connection.active();
                    break;
                }
                case PunchingProtos.MsgType.ReqRelayPunchingType_VALUE: {
                    PunchingProtos.PunchingData punchingData = PunchingProtos.PunchingData.parseFrom(request.getData());
                    logger.debug("recvReqPunching: {}:{} -> {}:{}",
                            punchingData.getPingHost(), punchingData.getPingPort(),
                            punchingData.getPongHost(), punchingData.getPongPort());
                    PunchingProtos.PunchingMessage message = PunchingProtos.PunchingMessage.newBuilder()
                            .setChannelId(request.getChannelId())
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
                case PunchingProtos.MsgType.RespRelayPunchingType_VALUE: {
                    String host = sender.getHostString();
                    int port = sender.getPort();
                    logger.debug("recvRespPunching: {}:{}", host, port);
                    PunchingClientConnection connection = (PunchingClientConnection) connectionManager.getConnection(request.getChannelId());
                    connection.updatePunchingPort(port);
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
