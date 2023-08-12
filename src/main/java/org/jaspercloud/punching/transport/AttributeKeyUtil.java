package org.jaspercloud.punching.transport;

import io.netty.channel.Channel;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;

public class AttributeKeyUtil {

    private AttributeKeyUtil() {

    }

    public static <T> Attribute<T> connectionData(Channel channel) {
        AttributeKey<T> key = AttributeKey.valueOf("connectionData");
        Attribute<T> attr = channel.attr(key);
        return attr;
    }
}