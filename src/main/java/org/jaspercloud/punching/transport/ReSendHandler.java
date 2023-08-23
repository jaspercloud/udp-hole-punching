package org.jaspercloud.punching.transport;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import org.jaspercloud.punching.proto.PunchingProtos;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ReSendHandler extends ChannelDuplexHandler {

    private long retryTime;
    private long timeout;

    private Map<String, Packet> map = new ConcurrentHashMap<>();

    public ReSendHandler(long retryTime, long timeout) {
        this.retryTime = retryTime;
        this.timeout = timeout;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        Channel channel = ctx.channel();
        new Runnable() {
            @Override
            public void run() {
                try {
                    Iterator<Map.Entry<String, Packet>> iterator = map.entrySet().iterator();
                    while (iterator.hasNext()) {
                        Map.Entry<String, Packet> next = iterator.next();
                        Packet packet = next.getValue();
                        boolean isTimeout = packet.isTimeout(timeout);
                        if (isTimeout) {
                            ctx.fireExceptionCaught(new TimeoutException(String.format("send timeout: %s", next.getKey())));
                            ctx.close();
                            return;
                        }
                        ctx.writeAndFlush(packet.getEnvelope());
                    }
                } finally {
                    if (channel.isActive()) {
                        ctx.executor().schedule(this, retryTime, TimeUnit.MILLISECONDS);
                    }
                }
            }
        }.run();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        Envelope<PunchingProtos.PunchingMessage> envelope = (Envelope<PunchingProtos.PunchingMessage>) msg;
        map.remove(envelope.message().getReqId());
        super.channelRead(ctx, msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        Envelope<PunchingProtos.PunchingMessage> envelope = (Envelope<PunchingProtos.PunchingMessage>) msg;
        if (envelope.reSend()) {
            map.put(envelope.message().getReqId(), new Packet(envelope));
            ctx.writeAndFlush(msg);
        } else {
            ctx.writeAndFlush(msg);
        }
    }

    private static class Packet {

        private Envelope<PunchingProtos.PunchingMessage> envelope;
        private Long time = System.currentTimeMillis();

        public Envelope<PunchingProtos.PunchingMessage> getEnvelope() {
            return envelope;
        }

        public Long getTime() {
            return time;
        }

        public Packet(Envelope<PunchingProtos.PunchingMessage> envelope) {
            this.envelope = envelope;
        }

        public boolean isTimeout(long timeout) {
            long diff = System.currentTimeMillis() - time;
            boolean result = diff > timeout;
            return result;
        }
    }
}
