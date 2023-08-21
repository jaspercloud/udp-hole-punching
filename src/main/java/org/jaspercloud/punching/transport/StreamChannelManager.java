package org.jaspercloud.punching.transport;

import io.netty.channel.*;
import org.jaspercloud.punching.proto.PunchingProtos;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ChannelHandler.Sharable
public class StreamChannelManager extends SimpleChannelInboundHandler<Envelope<PunchingProtos.PunchingMessage>> {

    private ChannelInitializer<Channel> initializer;
    private Map<String, StreamChannel> StreamMap = new ConcurrentHashMap<>();

    public StreamChannelManager(ChannelInitializer<Channel> initializer) {
        this.initializer = initializer;
    }

    public void addStreamChannel(StreamChannel channel) {
        StreamMap.put(channel.id().asLongText(), channel);
        channel.closeFuture().addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                StreamMap.remove(channel.id().asLongText());
            }
        });
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Envelope<PunchingProtos.PunchingMessage> msg) throws Exception {
        PunchingProtos.PunchingMessage request = msg.message();
        StreamChannel streamChannel = StreamMap.get(request.getStreamId());
        if (null == streamChannel) {
            streamChannel = StreamChannel.create((TunnelChannel) ctx.channel(), request.getStreamId(), initializer);
            addStreamChannel(streamChannel);
        }
        streamChannel.setLocalAddress(msg.recipient());
        streamChannel.setRemoteAddress(msg.sender());
        streamChannel.receive(msg);
    }

}
