package org.jaspercloud.punching.transport;

import com.google.protobuf.InvalidProtocolBufferException;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import org.jaspercloud.punching.exception.ParseException;
import org.jaspercloud.punching.proto.PunchingProtos;

public class ProtosUtil {

    private ProtosUtil() {
    }

    public static ByteBuf toBuffer(ByteBufAllocator alloc, byte[] bytes) {
        ByteBuf buffer = alloc.buffer();
        buffer.writeBytes(bytes);
        return buffer;
    }

    public static ByteBuf toBuffer(ByteBufAllocator alloc, PunchingProtos.PunchingMessage message) {
        ByteBuf buffer = alloc.buffer();
        buffer.writeBytes(message.toByteArray());
        return buffer;
    }

    public static PunchingProtos.PunchingMessage toProto(ByteBuf byteBuf) {
        try {
            byte[] bytes = new byte[byteBuf.readableBytes()];
            byteBuf.readBytes(bytes);
            PunchingProtos.PunchingMessage message = PunchingProtos.PunchingMessage.parseFrom(bytes);
            return message;
        } catch (InvalidProtocolBufferException e) {
            throw new ParseException(e.getMessage(), e);
        }
    }
}
