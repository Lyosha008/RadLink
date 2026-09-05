package org.Lyosha008.radlink;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;

public class CustomDynamicSlot extends SlotItemHandler {
    private final CustomSlotManager.SlotConfig config;
    private final Player player;

    public CustomDynamicSlot(IItemHandler itemHandler, int index, int xPosition, int yPosition, CustomSlotManager.SlotConfig config, Player player) {
        super(itemHandler, index, xPosition, yPosition);
        this.config = config;
        this.player = player;
    }

    @Override
    public boolean isActive() {
        if (config == null || config.ifCondition == null) {
            return true;
        }

        if (player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            return ScriptEngine.checkNestedCondition(config.ifCondition, serverLevel, player.blockPosition(), player);
        }

        return true;
    }

    @Override
    public boolean mayPlace(@NotNull ItemStack stack) {
        if (config != null && config.allowedItem != null && !config.allowedItem.isBlank()) {
            net.minecraft.resources.ResourceLocation itemId = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
            return itemId != null && itemId.toString().equals(config.allowedItem) && super.mayPlace(stack);
        }
        return super.mayPlace(stack);
    }

    @Override
    public int getMaxStackSize() {
        return config != null ? config.maxStack : 64;
    }
}