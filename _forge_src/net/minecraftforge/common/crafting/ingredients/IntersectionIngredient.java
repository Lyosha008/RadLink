/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.common.crafting.ingredients;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntComparators;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Ingredient that matches if all child ingredients match */
public class IntersectionIngredient extends AbstractIngredient {
    /**
     * Gets an intersection ingredient
     * @param ingredients  List of ingredients to match
     * @return  Ingredient that only matches if all the passed ingredients match
     */
    public static Ingredient of(Ingredient... ingredients) {
        if (ingredients.length == 0)
            throw new IllegalArgumentException("Cannot create an IntersectionIngredient with no children, use Ingredient.of() to create an empty ingredient");
        if (ingredients.length == 1)
            return ingredients[0];

        return new IntersectionIngredient(Arrays.asList(ingredients));
    }

    private final List<Ingredient> children;
    private final boolean isSimple;
    private ItemStack[] intersectedMatchingStacks = null;
    private IntList packedMatchingStacks = null;

    private IntersectionIngredient(List<Ingredient> children) {
        if (children.size() < 2)
            throw new IllegalArgumentException("Cannot create an IntersectionIngredient with one or no children");
        this.children = Collections.unmodifiableList(children);
        this.isSimple = children.stream().allMatch(Ingredient::isSimple);
    }

    @Override
    public boolean test(@Nullable ItemStack stack) {
        if (stack == null || stack.m_41619_())
            return false;

        for (Ingredient ingredient : children)
            if (!ingredient.test(stack))
                return false;

        return true;
    }

    @Override
    public ItemStack[] m_43908_() {
        if (this.intersectedMatchingStacks == null) {
            this.intersectedMatchingStacks = Arrays
                .stream(children.get(0).m_43908_())
                .filter(stack -> {
                    // the first ingredient is treated as a base, filtered by the second onwards
                    for (int i = 1; i < children.size(); i++)
                        if (!children.get(i).test(stack))
                            return false;
                    return true;
                })
                .toArray(ItemStack[]::new);
        }
        return intersectedMatchingStacks;
    }

    @Override
    public boolean m_43947_() {
        return children.stream().anyMatch(Ingredient::m_43947_);
    }

    @Override
    public boolean isSimple() {
        return isSimple;
    }

    @Override
    protected void invalidate() {
        super.invalidate();
        this.intersectedMatchingStacks = null;
        this.packedMatchingStacks = null;
    }

    @Override
    public IntList m_43931_() {
        if (this.packedMatchingStacks == null || checkInvalidation()) {
            markValid();
            var matchingStacks = m_43908_();
            this.packedMatchingStacks = new IntArrayList(matchingStacks.length);
            for (var stack : matchingStacks)
                this.packedMatchingStacks.add(StackedContents.m_36496_(stack));
            this.packedMatchingStacks.sort(IntComparators.NATURAL_COMPARATOR);
        }
        return packedMatchingStacks;
    }

    @Override
    public IIngredientSerializer<IntersectionIngredient> serializer() {
        return SERIALIZER;
    }

    public static final Codec<IntersectionIngredient> CODEC = RecordCodecBuilder.create(builder ->
        builder.group(
            Ingredient.f_291570_.listOf().fieldOf("children").forGetter(i -> i.children)
        )
        .apply(builder, IntersectionIngredient::new)
    );

    public static final IIngredientSerializer<IntersectionIngredient> SERIALIZER = new IIngredientSerializer<>() {
        @Override
        public Codec<? extends IntersectionIngredient> codec() {
            return CODEC;
        }

        @Override
        public IntersectionIngredient read(FriendlyByteBuf buffer) {
            return new IntersectionIngredient(buffer.m_236845_(Ingredient::m_43940_));
        }

        @Override
        public void write(FriendlyByteBuf buffer, IntersectionIngredient value) {
            buffer.m_236828_(value.children, (b, i) -> i.m_43923_(b));
        }
    };
}
