package org.jaspercloud.punching.transport;

import io.netty.channel.*;

public class ReWriteHandler extends ChannelOutboundHandlerAdapter {

    private Channel parent;

    public ReWriteHandler(Channel parent) {
        this.parent = parent;
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        ChannelFuture channelFuture = parent.writeAndFlush(msg);
        channelFuture.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                promise.trySuccess();
            }
        });
    }
}
