package org.Lyosha008.radlink;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Radlink.MOD_ID)
public class ContainerSlotHandler {

    @SubscribeEvent
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (event.getContainer() instanceof InventoryMenu menu) {
            Player player = event.getEntity();
            var handler = CustomSlotManager.getPlayerHandler(player);

            for (CustomSlotManager.SlotConfig config : CustomSlotManager.REGISTERED_SLOTS.values()) {
                menu.slots.add(new CustomDynamicSlot(
                    handler,
                    config.index,
                    config.x,
                    config.y,
                    config,
                    player
                ));
            }
        }
    }
}