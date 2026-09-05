package org.Lyosha008.radlink;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;

public class PacketOpenOverlay {
    private final String texture;
    private final int duration;

    public PacketOpenOverlay(String texture, int duration) {
        this.texture = texture;
        this.duration = duration;
    }

    public static void encode(PacketOpenOverlay msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.texture);
        buf.writeInt(msg.duration);
    }

    public static PacketOpenOverlay decode(FriendlyByteBuf buf) {
        return new PacketOpenOverlay(buf.readUtf(), buf.readInt());
    }

    public static void handle(PacketOpenOverlay msg, CustomPayloadEvent.Context ctx) {
        ctx.enqueueWork(() -> {
            ClientAnimationHandler.addFrameToQueue(msg.texture, msg.duration);
        });
        ctx.setPacketHandled(true);
    }
}