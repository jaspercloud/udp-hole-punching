package org.jaspercloud.punching.transport;

import io.netty.channel.AddressedEnvelope;
import io.netty.channel.DefaultAddressedEnvelope;
import org.jaspercloud.punching.proto.PunchingProtos;

import java.net.InetSocketAddress;

public class AddressedEnvelopeBuilder {

    private PunchingProtos.PunchingMessage message;
    private InetSocketAddress sender;

    public AddressedEnvelopeBuilder message(PunchingProtos.PunchingMessage message) {
        this.message = message;
        return this;
    }

    public AddressedEnvelopeBuilder sender(InetSocketAddress sender) {
        this.sender = sender;
        return this;
    }

    public AddressedEnvelope<PunchingProtos.PunchingMessage, InetSocketAddress> build() {
        AddressedEnvelope<PunchingProtos.PunchingMessage, InetSocketAddress> envelope = new DefaultAddressedEnvelope<>(message, null, sender);
        return envelope;
    }
}
