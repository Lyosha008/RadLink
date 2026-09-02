package org.AtomLink.radlink;

import com.google.gson.JsonArray;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import java.util.List;

public class CustomArmorItem extends ArmorItem {
    private final String id;
    private final String armorTextureRef;
    private final JsonArray lore;

    public CustomArmorItem(String id, net.minecraft.world.item.ArmorMaterial material, ArmorItem.Type type, int maxStackSize, String armorTextureRef, JsonArray lore) {
        super(material, type, new Item.Properties().stacksTo(maxStackSize));
        this.id = id;
        this.armorTextureRef = armorTextureRef;
        this.lore = lore;
    }

    @Override
    public Component getName(ItemStack stack) {return Component.translatable("item." + Radlink.MOD_ID + "." + id);}

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        LoreParser.parseLore(this.lore, tooltip);
    }

    @Override
    public String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
        if (armorTextureRef != null && !armorTextureRef.isBlank()) {
            String namespace = Radlink.MOD_ID;
            String path = armorTextureRef;
            if (armorTextureRef.contains(":")) {
                String[] parts = armorTextureRef.split(":", 2);
                namespace = parts[0];
                path = parts[1];
            }
            if (path.contains("/")) {
                String[] segs = path.split("/");
                path = segs[segs.length - 1];
            }
            int layer = (slot == EquipmentSlot.LEGS) ? 2 : 1;
            return namespace + ":textures/models/armor/" + path + "_layer_" + layer + ".png";
        }
        return super.getArmorTexture(stack, entity, slot, type);
    }
}
