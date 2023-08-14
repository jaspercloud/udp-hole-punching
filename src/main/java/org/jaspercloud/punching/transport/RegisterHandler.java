package org.jaspercloud.punching.transport;

import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.ReferenceCountUtil;
import org.jaspercloud.punching.proto.PunchingProtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class RegisterHandler extends ChannelDuplexHandler {

    private Logger logger = LoggerFactory.getLogger(getClass());

    private InetSocketAddress serverAddress;

    public RegisterHandler(InetSocketAddress serverAddress) {
        this.serverAddress = serverAddress;
    }

    @Override
    public void bind(ChannelHandlerContext ctx, SocketAddress localAddress, ChannelPromise promise) throws Exception {
        ChannelPipeline pipeline = ctx.pipeline();
        pipeline.addFirst("registerRead", new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                processChannelRead(ctx, msg, promise);
            }
        });
        ChannelPromise bindPromise = ctx.newPromise();
        bindPromise.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                Channel channel = ctx.channel();
                channel.eventLoop().scheduleAtFixedRate(() -> {
                    PunchingProtos.PunchingMessage message = PunchingProtos.PunchingMessage.newBuilder()
                            .setType(PunchingProtos.MsgType.ReqRegisterType)
                            .setReqId(UUID.randomUUID().toString())
                            .build();
                    ByteBuf byteBuf = ProtosUtil.toBuffer(channel.alloc(), message);
                    DatagramPacket packet = new DatagramPacket(byteBuf, serverAddress);
                    logger.debug("sendRegister: {}:{}", serverAddress.getHostString(), serverAddress.getPort());
                    channel.writeAndFlush(packet);
                }, 0, 5, TimeUnit.SECONDS);
            }
        });
        super.bind(ctx, localAddress, bindPromise);
    }

    private void processChannelRead(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        boolean release = true;
        try {
            DatagramPacket packet = (DatagramPacket) msg;
            ByteBuf byteBuf = packet.content();
            byteBuf.markReaderIndex();
            PunchingProtos.PunchingMessage request = ProtosUtil.toProto(byteBuf);
            switch (request.getType().getNumber()) {
                case PunchingProtos.MsgType.RespRegisterType_VALUE: {
                    PunchingProtos.ConnectionData connectionData = PunchingProtos.ConnectionData.parseFrom(request.getData());
                    AttributeKeyUtil.connectionData(ctx.channel()).set(connectionData);
                    InetSocketAddress localAddress = (InetSocketAddress) ctx.channel().localAddress();
                    logger.debug("recvRegister: {} -> {}:{}",
                            localAddress.getPort(),
                            connectionData.getHost(), connectionData.getPort());
                    if (!promise.isDone()) {
                        promise.setSuccess();
                    }
                    break;
                }
                default: {
                    release = false;
                    byteBuf.resetReaderIndex();
                    super.channelRead(ctx, msg);
                }
            }
        } finally {
            if (release) {
                ReferenceCountUtil.release(msg);
            }
        }
    }
}
