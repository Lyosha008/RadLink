package org.Lyosha008.radlink;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

@Mod.EventBusSubscriber(modid = Radlink.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ItemUseHandler {

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!event.isCanceled()) {
            dispatch(event.getEntity(), event.getItemStack(), event.getEntity().blockPosition(), "right_click");
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.isCanceled()) {
            dispatch(event.getEntity(), event.getItemStack(), event.getPos(), "right_click_block");
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!event.isCanceled() && event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.START) {
            dispatch(event.getEntity(), event.getItemStack(), event.getPos(), "left_click_block");
        }
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (!event.isCanceled()) {
            dispatch(event.getEntity(), event.getEntity().getMainHandItem(),
                event.getTarget().blockPosition(), "attack_entity");
        }
    }

    @SubscribeEvent
    public static void onInteractEntity(PlayerInteractEvent.EntityInteractSpecific event) {
        if (!event.isCanceled()) {
            dispatch(event.getEntity(), event.getItemStack(), event.getTarget().blockPosition(), "interact_entity");
        }
    }

    private static void dispatch(Player player, ItemStack stack, BlockPos position, String trigger) {
        if (!(player instanceof ServerPlayer serverPlayer) || stack.isEmpty()) {
            return;
        }

        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (itemId == null) {
            return;
        }

        BlockState blockState = trigger.endsWith("_block")
            ? serverPlayer.serverLevel().getBlockState(position)
            : null;

        CustomDefinitions.CustomScriptDefinition globalScript = Radlink.getGlobalScriptForElement(itemId.toString());
        executeIfTriggered(globalScript, trigger, blockState, position, serverPlayer);

        CustomDefinitions.CustomScriptDefinition itemScript = Radlink.getItemScriptForElement(itemId.toString());
        if (itemScript == null || !matchesTrigger(itemScript, trigger)) {
            return;
        }

        if (stack.getItem() instanceof CustomItem customItem && !customItem.matchesTriggerCondition(serverPlayer)) {
            return;
        }

        executeIfTriggered(itemScript, trigger, blockState, position, serverPlayer);
    }

    private static void executeIfTriggered(
        CustomDefinitions.CustomScriptDefinition script,
        String trigger,
        BlockState blockState,
        BlockPos position,
        ServerPlayer player
    ) {
        if (script == null || !matchesTrigger(script, trigger)) {
            return;
        }

        if (script.ifCondition() == null
            || ScriptEngine.checkConditions(script.ifCondition(), blockState, player.serverLevel(), position, player)) {
            ScriptEngine.executeActions(script.thenActions(), player.serverLevel(), position, player);
        }
    }

    private static boolean matchesTrigger(CustomDefinitions.CustomScriptDefinition script, String trigger) {
        return script.trigger() != null && trigger.equalsIgnoreCase(script.trigger().trim());
    }
}
