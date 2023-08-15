package org.jaspercloud.punching.transport;

import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.lang3.StringUtils;
import org.jaspercloud.punching.proto.PunchingProtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class ConnectionManager implements InitializingBean {

    private Logger logger = LoggerFactory.getLogger(getClass());

    private Map<String, PunchingConnection> connectionMap = new ConcurrentHashMap<>();

    @Override
    public void afterPropertiesSet() throws Exception {
        new Thread(() -> {
            while (true) {
                Iterator<PunchingConnection> iterator = connectionMap.values().iterator();
                while (iterator.hasNext()) {
                    PunchingConnection next = iterator.next();
                    long now = System.currentTimeMillis();
                    long diff = now - next.getPingTime();
                    if (diff >= 30000) {
                        next.close();
                        iterator.remove();
                    }
                }
            }
        }).start();
    }

    public boolean addConnection(PunchingConnection connection) {
        AtomicReference<Boolean> ref = new AtomicReference<>(false);
        connectionMap.computeIfAbsent(connection.getId(), key -> {
            ref.set(true);
            return connection;
        });
        Boolean result = ref.get();
        return result;
    }

    public PunchingConnection getConnection(String channelId) {
        return connectionMap.get(channelId);
    }

    public boolean channelRead(ChannelHandlerContext ctx, Envelope<PunchingProtos.PunchingMessage> envelope) {
        boolean read = false;
        for (PunchingConnection connection : connectionMap.values()) {
            if (connection instanceof PunchingClientConnection) {
                try {
                    PunchingClientConnection localConnection = (PunchingClientConnection) connection;
                    if (StringUtils.equals(localConnection.getId(), envelope.message().getChannelId())) {
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
