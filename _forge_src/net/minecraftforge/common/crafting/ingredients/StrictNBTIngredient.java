/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.common.crafting.ingredients;

import java.util.stream.Stream;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Ingredient that matches the given stack, performing an exact NBT match. Use {@link PartialNBTIngredient} if you need partial match. */
public class StrictNBTIngredient extends AbstractIngredient {
    public static StrictNBTIngredient of(ItemStack stack) {
        return new StrictNBTIngredient(stack);
    }

    private final ItemStack stack;
    private StrictNBTIngredient(ItemStack stack) {
        super(Stream.of(new Ingredient.ItemValue(stack)));
        this.stack = stack;
    }

    @Override
    public boolean test(@Nullable ItemStack input) {
        if (input == null)
            return false;
        //Can't use areItemStacksEqualUsingNBTShareTag because it compares stack size as well
        return this.stack.m_41720_() == input.m_41720_() && this.stack.m_41773_() == input.m_41773_() && this.stack.areShareTagsEqual(input);
    }

    @Override
    public boolean isSimple() {
        return false;
    }

    @Override
    public IIngredientSerializer<? extends Ingredient> serializer() {
        return SERIALIZER;
    }

    public static final Codec<StrictNBTIngredient> CODEC = RecordCodecBuilder.create(b -> b.group(
        ItemStack.f_41582_.fieldOf("stack").forGetter(i -> i.stack)
    ).apply(b, StrictNBTIngredient::new));

    public static final IIngredientSerializer<StrictNBTIngredient> SERIALIZER = new IIngredientSerializer<>() {
        @Override
        public Codec<? extends StrictNBTIngredient> codec() {
            return CODEC;
        }

        @Override
        public void write(FriendlyByteBuf buffer, StrictNBTIngredient value) {
            buffer.m_130055_(value.stack);
        }

        @Override
        public StrictNBTIngredient read(FriendlyByteBuf buffer) {
            return new StrictNBTIngredient(buffer.m_130267_());
        }
    };
}
