package org.jaspercloud.punching.transport;

import io.netty.buffer.ByteBuf;
import io.netty.channel.AddressedEnvelope;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import org.jaspercloud.punching.proto.PunchingProtos;

import java.net.InetSocketAddress;

public class Encoder extends ChannelOutboundHandlerAdapter {

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof Envelope) {
            Envelope<PunchingProtos.PunchingMessage> envelope = (Envelope<PunchingProtos.PunchingMessage>) msg;
            AddressedEnvelope<ByteBuf, InetSocketAddress> addressedEnvelope = Envelope.<ByteBuf>builder()
                    .sender(envelope.sender())
                    .recipient(envelope.recipient())
                    .message(ProtosUtil.toBuffer(ctx.alloc(), envelope.message()))
                    .toNettyEnvelope();
            ctx.write(addressedEnvelope, promise);
        } else {
            super.write(ctx, msg, promise);
        }
    }
}
