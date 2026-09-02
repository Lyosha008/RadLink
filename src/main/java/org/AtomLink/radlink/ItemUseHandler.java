package org.AtomLink.radlink;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

@Mod.EventBusSubscriber(modid = Radlink.MOD_ID)
public class ItemUseHandler {

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        ItemStack stack = event.getItemStack();
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());

        if (itemId == null) return;

        CustomDefinitions.CustomScriptDefinition script = Radlink.getScriptForElement(itemId.toString());

        if (script != null && "right_click".equalsIgnoreCase(script.trigger())) {
            if (script.ifCondition() == null || ScriptEngine.checkConditions(script.ifCondition(), null, level, player.blockPosition(), player)) {
                ScriptEngine.executeActions(script.thenActions(), level, player.blockPosition(), player);
            }
        }
    }
}