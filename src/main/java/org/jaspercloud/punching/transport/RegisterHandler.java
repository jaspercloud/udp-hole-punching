//package org.jaspercloud.punching.transport;
//
//import io.netty.channel.*;
//import org.jaspercloud.punching.proto.PunchingProtos;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.net.InetSocketAddress;
//import java.net.SocketAddress;
//import java.util.UUID;
//import java.util.concurrent.TimeUnit;
//
//public class RegisterHandler extends ChannelDuplexHandler {
//
//    private Logger logger = LoggerFactory.getLogger(getClass());
//
//    private InetSocketAddress serverAddress;
//
//    public RegisterHandler(InetSocketAddress serverAddress) {
//        this.serverAddress = serverAddress;
//    }
//
//    @Override
//    public void bind(ChannelHandlerContext ctx, SocketAddress localAddress, ChannelPromise promise) throws Exception {
//        ChannelPipeline pipeline = ctx.pipeline();
//        pipeline.addAfter("register", "registerRead", new ChannelInboundHandlerAdapter() {
//            @Override
//            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
//                Envelope<PunchingProtos.PunchingMessage> envelope = (Envelope<PunchingProtos.PunchingMessage>) msg;
//                processChannelRead(ctx, envelope, promise);
//            }
//        });
//        ChannelPromise bindPromise = ctx.newPromise();
//        bindPromise.addListener(new ChannelFutureListener() {
//            @Override
//            public void operationComplete(ChannelFuture future) throws Exception {
//                Channel channel = ctx.channel();
//                channel.eventLoop().scheduleAtFixedRate(() -> {
//                    PunchingProtos.PunchingMessage message = PunchingProtos.PunchingMessage.newBuilder()
//                            .setChannelId(ctx.channel().id().asLongText())
//                            .setType(PunchingProtos.MsgType.ReqRegisterType)
//                            .setReqId(UUID.randomUUID().toString())
//                            .build();
//                    Envelope envelope = Envelope.builder()
//                            .recipient(serverAddress)
//                            .message(message)
//                            .build();
//                    logger.debug("sendRegister: {}:{}", serverAddress.getHostString(), serverAddress.getPort());
//                    channel.writeAndFlush(envelope);
//                }, 0, 5, TimeUnit.SECONDS);
//            }
//        });
//        super.bind(ctx, localAddress, bindPromise);
//    }
//
//    private void processChannelRead(ChannelHandlerContext ctx, Envelope<PunchingProtos.PunchingMessage> envelope, ChannelPromise promise) throws Exception {
//        PunchingProtos.PunchingMessage request = envelope.message();
//        switch (request.getType().getNumber()) {
//            case PunchingProtos.MsgType.RespRegisterType_VALUE: {
//                PunchingProtos.ConnectionData connectionData = PunchingProtos.ConnectionData.parseFrom(request.getData());
//                AttributeKeyUtil.connectionData(ctx.channel()).set(connectionData);
//                InetSocketAddress localAddress = (InetSocketAddress) ctx.channel().localAddress();
//                logger.debug("recvRegister: {} -> {}:{}",
//                        localAddress.getPort(),
//                        connectionData.getHost(), connectionData.getPort());
//                if (!promise.isDone()) {
//                    promise.setSuccess();
//                }
//                break;
//            }
//            default: {
//                super.channelRead(ctx, envelope);
//            }
//        }
//    }
//}
