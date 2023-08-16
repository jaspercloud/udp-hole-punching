package org.jaspercloud.punching.transport.server;

import com.google.protobuf.InvalidProtocolBufferException;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.jaspercloud.punching.exception.ParseException;
import org.jaspercloud.punching.proto.PunchingProtos;
import org.jaspercloud.punching.transport.Envelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

public class ServerHandler extends SimpleChannelInboundHandler<Envelope<PunchingProtos.PunchingMessage>> {

    private Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Envelope<PunchingProtos.PunchingMessage> msg) {
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
        }
    }

    private void processRegister(ChannelHandlerContext ctx, Envelope<PunchingProtos.PunchingMessage> envelope) {
        PunchingProtos.PunchingMessage request = envelope.message();
        InetSocketAddress sender = envelope.sender();
        String host = sender.getHostString();
        int port = sender.getPort();
        logger.debug("recvRegister: {}:{}", host, port);
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

    private void processRelayPunching(ChannelHandlerContext ctx, Envelope<PunchingProtos.PunchingMessage> envelope) {
        try {
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
        } catch (InvalidProtocolBufferException e) {
            throw new ParseException(e.getMessage(), e);
        }
    }
}
