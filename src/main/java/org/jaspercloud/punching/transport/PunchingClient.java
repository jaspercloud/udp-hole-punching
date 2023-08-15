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
    private String localHost = "0.0.0.0";
    private int localPort;
    private Channel channel;
    private ConnectionManager connectionManager;
    private PunchingConnectionHandler connectionHandler;

    public String getServerHost() {
        return serverHost;
    }

    public int getServerPort() {
        return serverPort;
    }

    public String getLocalHost() {
        return localHost;
    }

    public int getLocalPort() {
        return localPort;
    }

    public Channel getChannel() {
        return channel;
    }

    public void setConnectionHandler(PunchingConnectionHandler connectionHandler) {
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
        InetSocketAddress local = new InetSocketAddress(localHost, localPort);
        NioEventLoopGroup group = new NioEventLoopGroup();
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group);
        bootstrap.channel(NioDatagramChannel.class);
        bootstrap.handler(new ChannelInitializer<DatagramChannel>() {
            @Override
            protected void initChannel(DatagramChannel ch) throws Exception {
                ChannelPipeline pipeline = ch.pipeline();
                pipeline.addLast("decoder", new Decoder());
                pipeline.addLast("encoder", new Encoder());
                pipeline.addLast("register", new RegisterHandler(serverAddress));
                pipeline.addLast("client", new ClientHandler(connectionManager));
                pipeline.addLast("connection", new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                        Envelope<PunchingProtos.PunchingMessage> envelope = (Envelope<PunchingProtos.PunchingMessage>) msg;
                        PunchingProtos.PunchingMessage message = envelope.message();
                        switch (message.getType().getNumber()) {
                            case PunchingProtos.MsgType.Data_VALUE: {
                                PunchingConnection connection = connectionManager.getConnection(message.getChannelId());
                                connectionHandler.onRead(connection, message.getData().toByteArray());
                                break;
                            }
                        }
                    }
                });
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
        PunchingLocalConnection connection = new PunchingLocalConnection(this, handler, id, host, port);
        connectionManager.addConnection(connection);
        return connection;
    }

    public ChannelFuture writeAndFlush(Envelope<PunchingProtos.PunchingMessage> envelope) {
        ChannelFuture future = channel.writeAndFlush(envelope);
        return future;
    }
}
