/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.common.extensions;

import java.util.UUID;
import java.util.function.Supplier;

import com.mojang.authlib.GameProfile;

import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

public interface IForgeGameTestHelper {
    private GameTestHelper self() {
        return (GameTestHelper)this;
    }

    default void assertTrue(boolean value, Supplier<String> message) {
       if (!value)
          throw new GameTestAssertException(message.get());
    }

    default void assertFalse(boolean value, Supplier<String> message) {
       if (value)
          throw new GameTestAssertException(message.get());
    }

    default ServerPlayer makeMockServerPlayer() {
        var level = self().m_177100_();
        var cookie = CommonListenerCookie.m_294081_(new GameProfile(UUID.randomUUID(), "test-mock-player"));
        var player = new ServerPlayer(level.m_7654_(), level, cookie.f_290628_(), cookie.f_290565_()) {
           public boolean m_5833_() {
              return false;
           }

           public boolean m_7500_() {
              return true;
           }
        };
        var connection = new Connection(PacketFlow.SERVERBOUND);
        var channel = new EmbeddedChannel(connection);
        channel.attr(Connection.f_290984_).set(ConnectionProtocol.PLAY.m_295783_(PacketFlow.SERVERBOUND));
        // This sets the connection/listener in the player
        new ServerGamePacketListenerImpl(level.m_7654_(), connection, player, cookie);
        return player;
    }
}
