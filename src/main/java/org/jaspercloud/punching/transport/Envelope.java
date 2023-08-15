package org.jaspercloud.punching.transport;

import io.netty.channel.AddressedEnvelope;
import io.netty.channel.DefaultAddressedEnvelope;

import java.net.InetSocketAddress;

public class Envelope<T> {

    private InetSocketAddress sender;
    private InetSocketAddress recipient;
    private T message;

    public InetSocketAddress sender() {
        return sender;
    }

    public InetSocketAddress recipient() {
        return recipient;
    }

    public T message() {
        return message;
    }

    public Envelope() {
    }

    public Envelope(InetSocketAddress sender, InetSocketAddress recipient, T message) {
        this.sender = sender;
        this.recipient = recipient;
        this.message = message;
    }

    public static class Builder<T> {

        private InetSocketAddress sender;
        private InetSocketAddress recipient;
        private T message;

        public Builder<T> sender(InetSocketAddress sender) {
            this.sender = sender;
            return this;
        }

        public Builder<T> recipient(InetSocketAddress recipient) {
            this.recipient = recipient;
            return this;
        }

        public Builder<T> message(T message) {
            this.message = message;
            return this;
        }

        public Envelope<T> build() {
            Envelope<T> envelope = new Envelope<>(sender, recipient, message);
            return envelope;
        }

        public AddressedEnvelope<T, InetSocketAddress> toNettyEnvelope() {
            AddressedEnvelope<T, InetSocketAddress> envelope = new DefaultAddressedEnvelope<>(message, recipient, sender);
            return envelope;
        }
    }

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }
}
