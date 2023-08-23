package org.jaspercloud.punching;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.ScheduledFuture;
import org.apache.commons.lang3.RandomUtils;
import org.jaspercloud.punching.transport.Envelope;
import org.junit.jupiter.api.Test;
import org.slf4j.impl.StaticLoggerBinder;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class QudpTest {

    @Test
    public void test() throws Exception {
        LoggerContext loggerContext = (LoggerContext) StaticLoggerBinder.getSingleton().getLoggerFactory();
        Logger root = loggerContext.getLogger("ROOT");
        root.setLevel(Level.INFO);
        Logger punching = loggerContext.getLogger("org.jaspercloud.punching");
        punching.setLevel(Level.DEBUG);

        {
            NioEventLoopGroup group = new NioEventLoopGroup();
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group);
            bootstrap.channel(NioDatagramChannel.class);
            bootstrap.handler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) throws Exception {
                    ChannelPipeline pipeline = ch.pipeline();
                    pipeline.addLast("codec", new CodecHandler());
                }
            });
            Channel channel = bootstrap.bind(1801).sync().channel();
        }
        {
            InetSocketAddress remote = new InetSocketAddress("localhost", 1801);
            NioEventLoopGroup group = new NioEventLoopGroup();
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group);
            bootstrap.channel(NioDatagramChannel.class);
            bootstrap.handler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) throws Exception {
                    ChannelPipeline pipeline = ch.pipeline();
                    pipeline.addLast("codec", new CodecHandler(remote));
                }
            });
            Channel channel = bootstrap.bind(1802).sync().channel();
            while (true) {
                ByteBuf buffer = channel.alloc().buffer();
                byte[] bytes = "test".getBytes(StandardCharsets.UTF_8);
                buffer.writeBytes(bytes);
                Envelope<ByteBuf> envelope = Envelope.<ByteBuf>builder()
                        .recipient(remote)
                        .message(buffer)
                        .build();
                channel.writeAndFlush(envelope);
                Thread.sleep(1000);
            }
        }
    }

    private class CodecHandler extends ChannelDuplexHandler {

        private static final int Send = 1;
        private static final int Ack = 2;
        private BufferQueue bufferQueue;

        private InetSocketAddress remote;
        private Runnable writeTask;
        private ScheduledFuture<?> scheduledFuture;

        public CodecHandler() {
        }

        public CodecHandler(InetSocketAddress remote) {
            this.remote = remote;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            bufferQueue = new BufferQueue();
            Channel channel = ctx.channel();
            EventLoop eventLoop = channel.eventLoop();
            writeTask = () -> {
                if (!ctx.channel().isActive()) {
                    scheduledFuture.cancel(true);
                    return;
                }
                Iterator<Map.Entry<Integer, ByteBufPromise>> iterator = bufferQueue.map.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<Integer, ByteBufPromise> next = iterator.next();
                    Integer index = next.getKey();
                    ByteBufPromise byteBufPromise = next.getValue();
                    long diff = System.currentTimeMillis() - byteBufPromise.getTime();
                    if (diff > 3000) {
                        ctx.fireExceptionCaught(new RuntimeException());
                        ctx.close();
                        return;
                    }
                    ByteBuf data = ctx.alloc().buffer();
                    data.writeInt(Send);
                    data.writeInt(index);
                    byteBufPromise.getByteBuf().markReaderIndex();
                    data.writeBytes(byteBufPromise.getByteBuf());
                    byteBufPromise.getByteBuf().resetReaderIndex();

                    AddressedEnvelope<ByteBuf, InetSocketAddress> dataEnvelope = new DefaultAddressedEnvelope<>(data, remote);
                    channel.writeAndFlush(dataEnvelope);
                }
            };
            scheduledFuture = eventLoop.scheduleAtFixedRate(writeTask, 0, 100, TimeUnit.MILLISECONDS);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof DatagramPacket) {
                DatagramPacket packet = (DatagramPacket) msg;
                InetSocketAddress sender = packet.sender();
                ByteBuf byteBuf = packet.content();
                int type = byteBuf.readInt();
                int index = byteBuf.readInt();
                switch (type) {
                    case Send: {
                        int rand = RandomUtils.nextInt(0, 100);
                        if (rand < 50) {
                            break;
                        }
                        System.out.println(String.format("rec: type=%s, index=%s", type, index));
                        ByteBuf retain = byteBuf.retain();
                        ctx.fireChannelRead(retain);
                        ByteBuf data = ctx.alloc().buffer();
                        data.writeInt(Ack);
                        data.writeInt(index);
                        AddressedEnvelope<ByteBuf, InetSocketAddress> dataEnvelope = new DefaultAddressedEnvelope<>(data, sender);
                        ctx.writeAndFlush(dataEnvelope);
                        break;
                    }
                    case Ack: {
                        System.out.println(String.format("ack: type=%s, index=%s", type, index));
                        bufferQueue.remove(index);
                        break;
                    }
                }
            }
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            if (msg instanceof Envelope) {
                Envelope<ByteBuf> envelope = (Envelope) msg;
                ByteBuf byteBuf = envelope.message();
                bufferQueue.add(byteBuf, promise);
                writeTask.run();
            } else if (msg instanceof AddressedEnvelope) {
                AddressedEnvelope<ByteBuf, InetSocketAddress> envelope = (AddressedEnvelope<ByteBuf, InetSocketAddress>) msg;
                ctx.writeAndFlush(envelope);
            }
        }
    }

    private class ByteBufPromise {

        private ByteBuf byteBuf;
        private ChannelPromise promise;
        private long time = System.currentTimeMillis();

        public ByteBuf getByteBuf() {
            return byteBuf;
        }

        public ChannelPromise getPromise() {
            return promise;
        }

        public long getTime() {
            return time;
        }

        public ByteBufPromise(ByteBuf byteBuf, ChannelPromise promise) {
            this.byteBuf = byteBuf;
            this.promise = promise;
        }
    }

    private class BufferQueue {

        private int index = 0;
        private Map<Integer, ByteBufPromise> map = new LinkedHashMap<>();
        private Lock lock = new ReentrantLock();

        public void add(ByteBuf byteBuf, ChannelPromise promise) {
            lock.lock();
            try {
                map.put(index++, new ByteBufPromise(byteBuf, promise));
            } finally {
                lock.unlock();
            }
        }

        public void remove(int index) {
            lock.lock();
            try {
                ByteBufPromise byteBufPromise = map.remove(index);
                if (null != byteBufPromise) {
                    ReferenceCountUtil.release(byteBufPromise.getByteBuf());
                    if (byteBufPromise.getPromise().isDone()) {
                        byteBufPromise.getPromise().trySuccess();
                    }
                }
            } finally {
                lock.unlock();
            }
        }
    }
}
