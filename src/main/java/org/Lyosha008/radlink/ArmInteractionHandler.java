package org.Lyosha008.radlink;

import net.minecraft.client.player.Input;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;

@Mod.EventBusSubscriber(modid = Radlink.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ArmInteractionHandler {

    private static double getArmState(Player player) {
        if (player == null) return 1.0;
        Map<String, Object> vars = Radlink.loadWorldVariables();
        Object val = vars.get("has_arm_" + player.getUUID().toString());

        if (val instanceof Number n) return n.doubleValue();
        if (val instanceof Boolean b) return b ? 1.0 : 3.0;
        return 1.0;
    }

    private static boolean isLeftArmDisabled(Player player) {
        double state = getArmState(player);
        return state == 2.0 || state == 4.0;
    }

    private static boolean isRightArmDisabled(Player player) {
        double state = getArmState(player);
        return state == 3.0 || state == 4.0;
    }

    private static boolean shouldBlockAction(Player player) {
        if (player == null) return false;
        boolean isMainRight = player.getMainArm() == HumanoidArm.RIGHT;
        if (isMainRight && isRightArmDisabled(player)) return true;
        return !isMainRight && isLeftArmDisabled(player);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !event.player.level().isClientSide()) {
            Player player = event.player;

            if (isLeftArmDisabled(player)) {
                InteractionHand leftHandSlot = (player.getMainArm() == HumanoidArm.RIGHT)
                    ? InteractionHand.OFF_HAND
                    : InteractionHand.MAIN_HAND;

                ItemStack item = player.getItemInHand(leftHandSlot);
                if (!item.isEmpty()) {
                    player.drop(item.copy(), true, false);
                    player.setItemInHand(leftHandSlot, ItemStack.EMPTY);
                }
            }

            if (isRightArmDisabled(player)) {
                InteractionHand rightHandSlot = (player.getMainArm() == HumanoidArm.RIGHT)
                    ? InteractionHand.MAIN_HAND
                    : InteractionHand.OFF_HAND;

                ItemStack item = player.getItemInHand(rightHandSlot);
                if (!item.isEmpty()) {
                    player.drop(item.copy(), true, false);
                    player.setItemInHand(rightHandSlot, ItemStack.EMPTY);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (shouldBlockAction(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (shouldBlockAction(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onBlockInteract(PlayerInteractEvent.RightClickBlock event) {
        if (shouldBlockAction(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (shouldBlockAction(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onItemUse(PlayerInteractEvent.RightClickItem event) {
        if (shouldBlockAction(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (shouldBlockAction(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @Mod.EventBusSubscriber(modid = Radlink.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static class ClientBoatHandler {
        @SubscribeEvent
        public static void onMovementInput(MovementInputUpdateEvent event) {
            Player player = event.getEntity();

            if (player != null && player.getVehicle() instanceof Boat) {
                Input input = event.getInput();

                if (input.up || input.down || input.left || input.right) {
                    boolean leftDisabled = isLeftArmDisabled(player);
                    boolean rightDisabled = isRightArmDisabled(player);

                    if (leftDisabled && !rightDisabled) {
                        input.up = false;
                        input.down = false;
                        input.right = false;
                        input.left = true;
                        input.leftImpulse = 1.0F;
                        input.forwardImpulse = 0.0F;
                    } else if (rightDisabled && !leftDisabled) {
                        input.up = false;
                        input.down = false;
                        input.left = false;
                        input.right = true;
                        input.leftImpulse = -1.0F;
                        input.forwardImpulse = 0.0F;
                    } else if (leftDisabled && rightDisabled) {
                        input.up = false;
                        input.down = false;
                        input.left = false;
                        input.right = false;
                        input.leftImpulse = 0.0F;
                        input.forwardImpulse = 0.0F;
                    }
                }
            }
        }
    }
}