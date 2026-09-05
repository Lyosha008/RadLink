package org.Lyosha008.radlink.mixin;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;
import net.minecraftforge.registries.ForgeRegistries;
import org.Lyosha008.radlink.CustomSlotManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryMenu.class)
public abstract class InventoryMenuMixin extends AbstractContainerMenu {

    protected InventoryMenuMixin(MenuType<?> pMenuType, int pContainerId) {
        super(pMenuType, pContainerId);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(Inventory playerInventory, boolean active, Player owner, CallbackInfo ci) {
        int slotIndex = 0;

        for (CustomSlotManager.SlotConfig config : CustomSlotManager.REGISTERED_SLOTS.values()) {
            int index = slotIndex++;
            this.addSlot(new SlotItemHandler(CustomSlotManager.getPlayerHandler(owner), index, config.x, config.y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    if (config.allowedItem.isEmpty()) return true;
                    ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
                    return itemId != null && itemId.toString().equals(config.allowedItem);
                }

                @Override
                public int getMaxStackSize() {
                    return config.maxStack;
                }
            });
        }
    }
}