package org.jaspercloud.punching;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import com.google.protobuf.ByteString;
import io.netty.channel.*;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.concurrent.ScheduledFuture;
import org.jaspercloud.punching.proto.PunchingProtos;
import org.jaspercloud.punching.transport.Decoder;
import org.jaspercloud.punching.transport.Encoder;
import org.jaspercloud.punching.transport.Envelope;
import org.jaspercloud.punching.transport.ReSendHandler;
import org.junit.jupiter.api.Test;
import org.slf4j.impl.StaticLoggerBinder;

import java.net.InetSocketAddress;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class EChannelTest {

    @Test
    public void test() throws Exception {
        LoggerContext loggerContext = (LoggerContext) StaticLoggerBinder.getSingleton().getLoggerFactory();
        Logger root = loggerContext.getLogger("ROOT");
        root.setLevel(Level.INFO);
        Logger punching = loggerContext.getLogger("org.jaspercloud.punching");
        punching.setLevel(Level.DEBUG);

        EmbeddedChannel embeddedChannel = new EmbeddedChannel(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ChannelPipeline pipeline = ch.pipeline();
                pipeline.addLast(new Decoder());
                pipeline.addLast(new Encoder());
                pipeline.addLast(new ReSendHandler(20, 3000));
                pipeline.addLast(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                        super.channelRead(ctx, msg);
                    }
                });
            }
        });
        new Thread(() -> {
            while (true) {
                try {
                    embeddedChannel.runPendingTasks();
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
        String id = UUID.randomUUID().toString();
        {
            PunchingProtos.PunchingMessage message = PunchingProtos.PunchingMessage.newBuilder()
                    .setChannelId("chId")
                    .setStreamId("sId")
                    .setReqId(id)
                    .setType(PunchingProtos.MsgType.PingType)
                    .setData(ByteString.EMPTY)
                    .build();
            Envelope<PunchingProtos.PunchingMessage> envelope = Envelope.<PunchingProtos.PunchingMessage>builder()
                    .recipient(new InetSocketAddress("localhost", 8888))
                    .message(message)
                    .build();
            embeddedChannel.writeOneOutbound(envelope);
        }
        Thread.sleep(1000);
        {
            PunchingProtos.PunchingMessage message = PunchingProtos.PunchingMessage.newBuilder()
                    .setChannelId("chId")
                    .setStreamId("sId")
                    .setReqId(id)
                    .setType(PunchingProtos.MsgType.PongType)
                    .setData(ByteString.EMPTY)
                    .build();
            Envelope<PunchingProtos.PunchingMessage> envelope = Envelope.<PunchingProtos.PunchingMessage>builder()
                    .recipient(new InetSocketAddress("localhost", 8888))
                    .message(message)
                    .build();
            embeddedChannel.writeOneInbound(envelope);
        }
        CountDownLatch countDownLatch = new CountDownLatch(1);
        countDownLatch.await();
    }
}
