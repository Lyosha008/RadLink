package org.Lyosha008.radlink;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;

public class NetworkHandler {
    private static final int PROTOCOL_VERSION = 1;

    public static final SimpleChannel INSTANCE = ChannelBuilder
        .named(new ResourceLocation(Radlink.MOD_ID, "main"))
        .networkProtocolVersion(PROTOCOL_VERSION)
        .simpleChannel();

    public static void register() {
        int id = 0;
        INSTANCE.messageBuilder(PacketOpenOverlay.class, id++)
            .encoder(PacketOpenOverlay::encode)
            .decoder(PacketOpenOverlay::decode)
            .consumerMainThread(PacketOpenOverlay::handle)
            .add();
    }

    public static void sendToPlayer(PacketOpenOverlay msg, ServerPlayer player) {
        INSTANCE.send(msg, PacketDistributor.PLAYER.with(player));
    }
}