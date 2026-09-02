package org.AtomLink.radlink;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

public class CustomArmorMaterial implements net.minecraft.world.item.ArmorMaterial {
    private final ArmorMaterials base;
    private final int durability;

    public CustomArmorMaterial(ArmorMaterials base, int durability) {
        this.base = base;
        this.durability = Math.max(durability, 1);
    }

    @Override
    public int getDurabilityForType(ArmorItem.Type type) {
        return durability;
    }

    @Override
    public int getDefenseForType(ArmorItem.Type type) {
        return base.getDefenseForType(type);
    }

    @Override
    public int getEnchantmentValue() {
        return base.getEnchantmentValue();
    }

    @Override
    public SoundEvent getEquipSound() {
        return base.getEquipSound();
    }

    @Override
    public Ingredient getRepairIngredient() {
        return base.getRepairIngredient();
    }

    @Override
    public String getName() {
        return base.getName();
    }

    @Override
    public float getToughness() {
        return base.getToughness();
    }

    @Override
    public float getKnockbackResistance() {
        return base.getKnockbackResistance();
    }
}
