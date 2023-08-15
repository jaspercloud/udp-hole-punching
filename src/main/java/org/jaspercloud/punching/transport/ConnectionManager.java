package org.jaspercloud.punching.transport;

import io.netty.channel.ChannelHandlerContext;
import org.jaspercloud.punching.proto.PunchingProtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class ConnectionManager implements InitializingBean {

    private Logger logger = LoggerFactory.getLogger(getClass());

    private Map<String, PunchingConnection> connectionMap = new ConcurrentHashMap<>();

    @Override
    public void afterPropertiesSet() throws Exception {

    }

    public void addConnection(PunchingConnection connection) {
        connectionMap.putIfAbsent(connection.getId(), connection);
    }

    public PunchingConnection getConnection(String channelId) {
        return connectionMap.get(channelId);
    }

    public boolean channelRead(ChannelHandlerContext ctx, Envelope<PunchingProtos.PunchingMessage> envelope) {
        boolean read = false;
        for (PunchingConnection connection : connectionMap.values()) {
            if (connection instanceof PunchingLocalConnection) {
                try {
                    PunchingLocalConnection localConnection = (PunchingLocalConnection) connection;
                    PunchingProtos.ConnectionData connectionData = (PunchingProtos.ConnectionData) AttributeKeyUtil.connectionData(ctx.channel()).get();
                    if (Objects.equals(envelope.recipient().getPort(), connectionData.getPort())) {
                        read = true;
                        localConnection.onChannelRead(ctx, envelope);
                    }
                } catch (Exception e) {
                    logger.error(e.getMessage(), e);
                }
            }
        }
        return read;
    }
}
