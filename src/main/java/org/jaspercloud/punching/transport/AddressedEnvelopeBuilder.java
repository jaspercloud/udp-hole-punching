package org.jaspercloud.punching.transport;

import io.netty.channel.AddressedEnvelope;
import io.netty.channel.DefaultAddressedEnvelope;
import org.jaspercloud.punching.proto.PunchingProtos;

import java.net.InetSocketAddress;

public class AddressedEnvelopeBuilder<T> {

    private T message;
    private InetSocketAddress sender;

    public AddressedEnvelopeBuilder message(T message) {
        this.message = message;
        return this;
    }

    public AddressedEnvelopeBuilder sender(InetSocketAddress sender) {
        this.sender = sender;
        return this;
    }

    public AddressedEnvelope<T, InetSocketAddress> build() {
        AddressedEnvelope<T, InetSocketAddress> envelope = new DefaultAddressedEnvelope<>(message, null, sender);
        return envelope;
    }
}
