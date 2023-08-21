package org.jaspercloud.punching.transport;

import io.netty.channel.*;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;

public class BusChannel extends AbstractChannel {

    private enum State {OPEN, ACTIVE, CLOSED}

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(EmbeddedChannel.class);

    private static final ChannelMetadata METADATA_NO_DISCONNECT = new ChannelMetadata(false);
    private static final ChannelMetadata METADATA_DISCONNECT = new ChannelMetadata(true);

    private final ChannelMetadata metadata = new ChannelMetadata(false);
    private final ChannelConfig config;
    private Throwable lastException;
    private BusChannel.State state;
    private SocketAddress localAddress;
    private SocketAddress remoteAddress;

    @Override
    public SocketAddress localAddress() {
        return localAddress;
    }

    @Override
    public SocketAddress remoteAddress() {
        return remoteAddress;
    }

    public void setLocalAddress(SocketAddress localAddress) {
        this.localAddress = localAddress;
    }

    public void setRemoteAddress(SocketAddress remoteAddress) {
        this.remoteAddress = remoteAddress;
    }

    /**
     * Create a new instance and an empty pipeline.
     */
    public BusChannel(Channel parent) {
        this(parent, DefaultChannelId.newInstance());
    }


    /**
     * Create a new instance with the channel ID set to the given ID and the pipeline
     * initialized with the specified handlers.
     */
    public BusChannel(Channel parent, ChannelId channelId) {
        super(parent, channelId);
        config = new DefaultChannelConfig(this);
    }

    public void receive(Object msg) {
        pipeline().fireChannelRead(msg);
    }

    @Override
    protected final DefaultChannelPipeline newChannelPipeline() {
        return new BusChannel.BusChannelPipeline(this);
    }

    @Override
    public ChannelMetadata metadata() {
        return metadata;
    }

    @Override
    public ChannelConfig config() {
        return config;
    }

    @Override
    public boolean isOpen() {
        return state != BusChannel.State.CLOSED;
    }

    @Override
    public boolean isActive() {
        return state == BusChannel.State.ACTIVE;
    }

    @Override
    public final ChannelFuture close() {
        return close(newPromise());
    }

    @Override
    public final ChannelFuture disconnect() {
        return disconnect(newPromise());
    }

    @Override
    public final ChannelFuture close(ChannelPromise promise) {
        // We need to call runPendingTasks() before calling super.close() as there may be something in the queue
        // that needs to be run before the actual close takes place.
        ChannelFuture future = super.close(promise);

        // Now finish everything else and cancel all scheduled tasks that were not ready set.
        return future;
    }

    @Override
    public final ChannelFuture disconnect(ChannelPromise promise) {
        ChannelFuture future = super.disconnect(promise);
        return future;
    }

    private void recordException(ChannelFuture future) {
        if (!future.isSuccess()) {
            recordException(future.cause());
        }
    }

    private void recordException(Throwable cause) {
        if (lastException == null) {
            lastException = cause;
        } else {
            logger.warn(
                    "More than one exception was raised. " +
                            "Will report only the first one and log others.", cause);
        }
    }

    /**
     * Checks for the presence of an {@link Exception}.
     */
    private ChannelFuture checkException(ChannelPromise promise) {
        Throwable t = lastException;
        if (t != null) {
            lastException = null;

            if (promise.isVoid()) {
                PlatformDependent.throwException(t);
            }

            return promise.setFailure(t);
        }

        return promise.setSuccess();
    }

    /**
     * Check if there was any {@link Throwable} received and if so rethrow it.
     */
    public void checkException() {
        checkException(voidPromise());
    }

    /**
     * Returns {@code true} if the {@link Channel} is open and records optionally
     * an {@link Exception} if it isn't.
     */
    private boolean checkOpen(boolean recordException) {
        if (!isOpen()) {
            if (recordException) {
                recordException(new ClosedChannelException());
            }
            return false;
        }

        return true;
    }

    /**
     * Ensure the {@link Channel} is open and if not throw an exception.
     */
    protected final void ensureOpen() {
        if (!checkOpen(true)) {
            checkException();
        }
    }

    @Override
    protected boolean isCompatible(EventLoop loop) {
        return loop instanceof EventLoop;
    }

    @Override
    protected SocketAddress localAddress0() {
        throw new UnsupportedOperationException();
    }

    @Override
    protected SocketAddress remoteAddress0() {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void doRegister() throws Exception {
        state = BusChannel.State.ACTIVE;
    }

    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
        this.localAddress = localAddress;
    }

    @Override
    protected void doDisconnect() throws Exception {
        if (!metadata.hasDisconnect()) {
            doClose();
        }
    }

    @Override
    protected void doClose() throws Exception {
        state = BusChannel.State.CLOSED;
    }

    @Override
    protected void doBeginRead() throws Exception {
        // NOOP
    }

    @Override
    protected AbstractUnsafe newUnsafe() {
        return new BusChannel.DefaultUnsafe();
    }

    @Override
    protected void doWrite(ChannelOutboundBuffer in) throws Exception {
        for (; ; ) {
            Object msg = in.current();
            if (msg == null) {
                break;
            }

            ReferenceCountUtil.retain(msg);
            handleOutboundMessage(msg);
            in.remove();
        }
    }

    /**
     * Called for each outbound message.
     *
     * @see #doWrite(ChannelOutboundBuffer)
     */
    protected void handleOutboundMessage(Object msg) {
    }

    /**
     * Called for each inbound message.
     */
    protected void handleInboundMessage(Object msg) {
    }

    private class DefaultUnsafe extends AbstractUnsafe {
        @Override
        public void connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
            BusChannel.this.localAddress = null != localAddress ? localAddress : new InetSocketAddress(0);
            BusChannel.this.remoteAddress = remoteAddress;
            safeSetSuccess(promise);
        }
    }

    private final class BusChannelPipeline extends DefaultChannelPipeline {

        public BusChannelPipeline(BusChannel channel) {
            super(channel);
        }

        @Override
        protected void onUnhandledInboundException(Throwable cause) {
            recordException(cause);
        }

        @Override
        protected void onUnhandledInboundMessage(Object msg) {
            handleInboundMessage(msg);
        }
    }
}
