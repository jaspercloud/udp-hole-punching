package org.jaspercloud.punching.transport;

import io.netty.channel.ChannelId;

public class RemoteChannelId implements ChannelId {

    private String id;

    public RemoteChannelId(String id) {
        this.id = id;
    }

    @Override
    public String asLongText() {
        return id;
    }

    @Override
    public String asShortText() {
        return id;
    }

    @Override
    public int compareTo(ChannelId o) {
        return 0;
    }
}
