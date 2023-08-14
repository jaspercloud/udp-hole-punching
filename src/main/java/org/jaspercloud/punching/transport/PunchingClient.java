package org.jaspercloud.punching.transport;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import org.jaspercloud.punching.proto.PunchingProtos;
import org.springframework.beans.factory.InitializingBean;

import java.net.InetSocketAddress;
import java.util.UUID;

public class PunchingClient implements InitializingBean {

    private String serverHost;
    private int serverPort;
    private int localPort;
    private Channel channel;
    private ConnectionManager connectionManager;
    private SimpleChannelInboundHandler<AddressedEnvelope<PunchingProtos.PunchingMessage, InetSocketAddress>> connectionHandler;

    public String getServerHost() {
        return serverHost;
    }

    public int getServerPort() {
        return serverPort;
    }

    public Channel getChannel() {
        return channel;
    }

    public void setConnectionHandler(SimpleChannelInboundHandler<AddressedEnvelope<PunchingProtos.PunchingMessage, InetSocketAddress>> connectionHandler) {
        this.connectionHandler = connectionHandler;
    }

    public PunchingClient(String host, int port) {
        this(host, port, 0);
    }

    public PunchingClient(String serverHost, int serverPort, int localPort) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.localPort = localPort;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        connectionManager = new ConnectionManager();
        connectionManager.afterPropertiesSet();
        InetSocketAddress serverAddress = new InetSocketAddress(serverHost, serverPort);
        InetSocketAddress local = new InetSocketAddress("0.0.0.0", localPort);
        NioEventLoopGroup group = new NioEventLoopGroup();
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group)
                .channel(NioDatagramChannel.class)
                .handler(new ChannelInitializer<DatagramChannel>() {
                    @Override
                    protected void initChannel(DatagramChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addFirst("register", new RegisterHandler(serverAddress));
                        pipeline.addLast("client", new ClientHandler(connectionManager));
                        pipeline.addLast("connection", connectionHandler);
                    }
                });
        channel = bootstrap.bind(local).sync().channel();
        channel.closeFuture().addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                group.shutdownGracefully();
            }
        });
    }

    public PunchingConnection createConnection(String host, int port, PunchingConnectionHandler handler) {
        String id = UUID.randomUUID().toString();
        PunchingConnection connection = new PunchingConnectionImpl(this, handler, id, host, port);
        connectionManager.addConnection(connection);
        return connection;
    }

    public ChannelFuture writeAndFlush(AddressedEnvelope<PunchingProtos.PunchingMessage, InetSocketAddress> envelope) {
        ChannelFuture future = channel.writeAndFlush(envelope);
        return future;
    }
}
