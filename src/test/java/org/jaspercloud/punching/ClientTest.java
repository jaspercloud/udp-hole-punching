package org.jaspercloud.punching;

import io.netty.channel.AddressedEnvelope;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.jaspercloud.punching.proto.PunchingProtos;
import org.jaspercloud.punching.transport.PunchingClient;

import java.net.InetSocketAddress;

public class ClientTest {

    public static void main(String[] args) throws Exception {
        PunchingClient punchingClient = new PunchingClient("127.0.0.1", 1080, 0);
        punchingClient.setConnectionHandler(new SimpleChannelInboundHandler<AddressedEnvelope<PunchingProtos.PunchingMessage, InetSocketAddress>>() {
            @Override
            protected void channelRead0(ChannelHandlerContext ctx, AddressedEnvelope<PunchingProtos.PunchingMessage, InetSocketAddress> msg) throws Exception {
                PunchingProtos.PunchingMessage message = msg.content();
                switch (message.getType().getNumber()) {
                    case PunchingProtos.MsgType.Data_VALUE: {
                        System.out.println("msg: " + new String(message.getData().toByteArray()));
                        break;
                    }
                }
            }
        });
        punchingClient.afterPropertiesSet();
    }
}
