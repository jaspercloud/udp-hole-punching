package org.jaspercloud.punching.transport;

import io.netty.channel.ChannelHandlerContext;
import org.jaspercloud.punching.proto.PunchingProtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ConnectionManager implements InitializingBean {

    private Logger logger = LoggerFactory.getLogger(getClass());

    private Map<String, PunchingConnection> connectionMap = new ConcurrentHashMap<>();

    @Override
    public void afterPropertiesSet() throws Exception {

    }

    public void addConnection(PunchingConnection connection) {
        connectionMap.put(connection.getId(), connection);
    }

    public void channelRead(ChannelHandlerContext ctx, Envelope<PunchingProtos.PunchingMessage> envelope) {
        for (PunchingConnection connection : connectionMap.values()) {
            try {
                connection.onChannelRead(ctx, envelope);
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }
        }
    }
}
