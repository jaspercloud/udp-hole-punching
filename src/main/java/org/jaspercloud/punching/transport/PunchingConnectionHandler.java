package org.jaspercloud.punching.transport;

import org.jaspercloud.punching.proto.PunchingProtos;

public interface PunchingConnectionHandler {

    default void onActive(PunchingConnection connection) {
    }

    default void onInActive(PunchingConnection connection) {
    }

    default void onRead(PunchingConnection connection, Envelope<PunchingProtos.PunchingMessage> envelope) {
    }
}
