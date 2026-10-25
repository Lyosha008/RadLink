package org.Lyosha008.radlink;

import com.google.gson.JsonArray;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.HorseArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import java.util.List;

public class CustomHorseArmorItem extends HorseArmorItem {
    private final String id;
    private final JsonArray lore;

    public CustomHorseArmorItem(String id, int protection, int maxStackSize, String texture, JsonArray lore) {
        super(Math.max(0, protection), resolveTexture(id, texture), new Item.Properties().stacksTo(maxStackSize));
        this.id = id;
        this.lore = lore;
    }

    @Override public Component getName(ItemStack stack) {return Component.translatable("item."+Radlink.MOD_ID +"."+id);}

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        LoreParser.parseLore(this.lore, tooltip);
    }

    private static ResourceLocation resolveTexture(String id, String texture) {
        if (texture == null || texture.isBlank()) {
            return new ResourceLocation(Radlink.MOD_ID,"textures/entity/horse/armor/" + id + ".png");
        }

        ResourceLocation location = new ResourceLocation(
            texture.contains(":") ? texture : Radlink.MOD_ID + ":" + texture
        );
        String path = location.getPath();
        if (!path.startsWith("textures/")) {
            path = path.startsWith("entity/") ? "textures/" + path : "textures/entity/horse/armor/" + path;
        }
        if (!path.endsWith(".png")) {path += ".png";}
        return new ResourceLocation(location.getNamespace(), path);
    }
}
