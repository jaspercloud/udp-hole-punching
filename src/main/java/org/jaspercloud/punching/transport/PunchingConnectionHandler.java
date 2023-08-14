package org.jaspercloud.punching.transport;

import io.netty.channel.AddressedEnvelope;
import org.jaspercloud.punching.proto.PunchingProtos;

import java.net.InetSocketAddress;

public interface PunchingConnectionHandler {

    default void onActive(PunchingConnection connection) {
    }

    default void onInActive(PunchingConnection connection) {
    }

    default void onRead(PunchingConnection connection, AddressedEnvelope<PunchingProtos.PunchingMessage, InetSocketAddress> envelope) {
    }
}
