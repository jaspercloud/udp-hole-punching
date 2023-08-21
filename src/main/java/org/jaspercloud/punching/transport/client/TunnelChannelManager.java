package org.jaspercloud.punching.transport.client;

import io.netty.channel.*;
import org.jaspercloud.punching.proto.PunchingProtos;
import org.jaspercloud.punching.transport.Envelope;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ChannelHandler.Sharable
public class TunnelChannelManager extends SimpleChannelInboundHandler<Envelope<PunchingProtos.PunchingMessage>> {

    private ChannelInitializer<Channel> initializer;
    private Map<String, TunnelChannel> TunnelMap = new ConcurrentHashMap<>();

    public TunnelChannelManager(ChannelInitializer<Channel> initializer) {
        this.initializer = initializer;
    }

    public void addTunnelChannel(TunnelChannel channel) {
        TunnelMap.put(channel.id().asLongText(), channel);
        channel.closeFuture().addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                TunnelMap.remove(channel.id().asLongText());
            }
        });
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Envelope<PunchingProtos.PunchingMessage> msg) throws Exception {
        PunchingProtos.PunchingMessage request = msg.message();
        TunnelChannel tunnelChannel = TunnelMap.get(request.getChannelId());
        if (null == tunnelChannel) {
            tunnelChannel = TunnelChannel.create(ctx.channel(), request.getChannelId(), initializer);
            addTunnelChannel(tunnelChannel);
        }
        tunnelChannel.receive(msg);
    }
}
