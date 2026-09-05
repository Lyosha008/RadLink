package org.Lyosha008.radlink;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderArmEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;

@Mod.EventBusSubscriber(modid = Radlink.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ArmRenderHandler {

    private static double getArmState(Player player) {
        if (player == null) return 1.0;
        Map<String, Object> vars = Radlink.loadWorldVariables();
        Object val = vars.get("has_arm_" + player.getUUID().toString());

        if (val instanceof Number n) return n.doubleValue();
        if (val instanceof Boolean b) return b ? 1.0 : 4.0;
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

    @SubscribeEvent
    public static void onRenderArm(RenderArmEvent event) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        if (isRightArmDisabled(player) && event.getArm() == HumanoidArm.RIGHT) event.setCanceled(true);
        if (isLeftArmDisabled(player) && event.getArm() == HumanoidArm.LEFT) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRenderPlayerPre(RenderLivingEvent.Pre<?, ?> event) {
        if (event.getEntity() instanceof Player player) {
            if (event.getRenderer().getModel() instanceof PlayerModel<?> model) {
                if (isRightArmDisabled(player)) {
                    model.rightArm.visible = false;
                    model.rightSleeve.visible = false;
                }
                if (isLeftArmDisabled(player)) {
                    model.leftArm.visible = false;
                    model.leftSleeve.visible = false;
                }
            }
        }
    }

    @SubscribeEvent
    public static void onRenderPlayerPost(RenderLivingEvent.Post<?, ?> event) {
        if (event.getEntity() instanceof Player) {
            if (event.getRenderer().getModel() instanceof PlayerModel<?> model) {
                model.rightArm.visible = true;
                model.rightSleeve.visible = true;
                model.leftArm.visible = true;
                model.leftSleeve.visible = true;
            }
        }
    }
}