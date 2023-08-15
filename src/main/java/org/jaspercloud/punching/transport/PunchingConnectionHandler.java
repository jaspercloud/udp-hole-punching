package org.jaspercloud.punching.transport;

public interface PunchingConnectionHandler {

    default void onActive(PunchingConnection connection) {
    }

    default void onInActive(PunchingConnection connection) {
    }

    default void onRead(PunchingConnection connection, byte[] data) {
    }
}
