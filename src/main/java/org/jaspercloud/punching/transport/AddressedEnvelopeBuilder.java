package org.jaspercloud.punching.transport;

import io.netty.channel.AddressedEnvelope;
import io.netty.channel.DefaultAddressedEnvelope;

import java.net.InetSocketAddress;

public class AddressedEnvelopeBuilder<T> {

    private T message;
    private InetSocketAddress sender;
    private InetSocketAddress recipient;

    public AddressedEnvelopeBuilder message(T message) {
        this.message = message;
        return this;
    }

    public AddressedEnvelopeBuilder sender(InetSocketAddress sender) {
        this.sender = sender;
        return this;
    }

    public AddressedEnvelopeBuilder recipient(InetSocketAddress recipient) {
        this.recipient = recipient;
        return this;
    }

    public AddressedEnvelope<T, InetSocketAddress> build() {
        AddressedEnvelope<T, InetSocketAddress> envelope = new DefaultAddressedEnvelope<>(message, recipient, sender);
        return envelope;
    }
}
