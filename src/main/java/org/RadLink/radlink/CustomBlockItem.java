package org.RadLink.radlink;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

public class CustomBlockItem extends BlockItem {
    private final String customName;

    public CustomBlockItem(Block block, String id, String customName, int maxStackSize) {
        super(block, new Properties().stacksTo(maxStackSize));
        this.customName = customName;
    }

    @Override
    public Component getName(ItemStack stack) {return Component.translatable("block.radlink." + this.customName);
    }
}