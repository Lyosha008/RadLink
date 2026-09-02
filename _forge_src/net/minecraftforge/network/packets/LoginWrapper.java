/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.network.packets;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record LoginWrapper(ResourceLocation channel, FriendlyByteBuf data) {
    public static LoginWrapper decode(FriendlyByteBuf buf) {
        var channel = buf.m_130281_();
        var len = buf.m_130242_();
        var data = new FriendlyByteBuf(buf.readBytes(len));
        return new LoginWrapper(channel, data);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.m_130085_(channel);
        buf.m_130130_(data.readableBytes());
        buf.writeBytes(data.slice());
    }
}
