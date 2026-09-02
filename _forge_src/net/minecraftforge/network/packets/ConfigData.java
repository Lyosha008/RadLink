/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.network.packets;

import net.minecraft.network.FriendlyByteBuf;

public record ConfigData(String name, byte[] data) {
    public void encode(final FriendlyByteBuf buf) {
        buf.m_130070_(this.name());
        buf.m_130087_(this.data());
    }

    public static ConfigData decode(FriendlyByteBuf buf) {
        return new ConfigData(buf.m_130277_(), buf.m_130052_());
    }
}