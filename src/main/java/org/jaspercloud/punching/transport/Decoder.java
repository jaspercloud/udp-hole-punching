package org.jaspercloud.punching.transport;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.ReferenceCountUtil;
import org.jaspercloud.punching.proto.PunchingProtos;

import java.net.InetSocketAddress;

public class Decoder extends ChannelInboundHandlerAdapter {

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof DatagramPacket) {
            try {
                DatagramPacket packet = (DatagramPacket) msg;
                InetSocketAddress sender = packet.sender();
                InetSocketAddress recipient = packet.recipient();
                PunchingProtos.PunchingMessage proto = ProtosUtil.toProto(packet.content());
                Envelope<PunchingProtos.PunchingMessage> envelope = Envelope.<PunchingProtos.PunchingMessage>builder()
                        .sender(sender)
                        .recipient(recipient)
                        .message(proto)
                        .build();
                ctx.fireChannelRead(envelope);
            } finally {
                ReferenceCountUtil.release(msg);
            }
        } else {
            super.channelRead(ctx, msg);
        }
    }
}
