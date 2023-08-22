package org.jaspercloud.punching.transport;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.concurrent.ScheduledFuture;
import org.jaspercloud.punching.proto.PunchingProtos;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ReSendHandler extends ChannelDuplexHandler {

    private long timeout;

    private Map<String, Packet> map = new ConcurrentHashMap<>();
    private Runnable writeTask;
    private ScheduledFuture<?> scheduledFuture;

    public ReSendHandler(long timeout) {
        this.timeout = timeout;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        writeTask = () -> {
            Iterator<Map.Entry<String, Packet>> iterator = map.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, Packet> next = iterator.next();
                Packet packet = next.getValue();
                boolean timeout = packet.isTimeout(this.timeout);
                if (timeout) {
                    ctx.fireExceptionCaught(new TimeoutException("send timeout"));
                    if (null != scheduledFuture) {
                        scheduledFuture.cancel(true);
                    }
                    ctx.close();
                    return;
                }
                ctx.writeAndFlush(packet.getEnvelope());
            }
        };
        scheduledFuture = ctx.executor().scheduleAtFixedRate(writeTask, 0, 100, TimeUnit.MILLISECONDS);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        if (null != scheduledFuture) {
            scheduledFuture.cancel(true);
        }
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
            writeTask.run();
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
