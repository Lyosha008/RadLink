package org.Lyosha008.radlink;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.LinkedList;
import java.util.Queue;

@Mod.EventBusSubscriber(modid = Radlink.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientAnimationHandler {

    private static class FrameData {
        final ResourceLocation texture;
        final int duration;

        FrameData(String path, int duration) {
            this.texture = new ResourceLocation(Radlink.MOD_ID, path);
            this.duration = duration;
        }
    }

    private static final Queue<FrameData> frameQueue = new LinkedList<>();
    private static FrameData currentFrame = null;
    private static int remainingTicks = 0;

    public static void addFrameToQueue(String texture, int duration) {
        frameQueue.add(new FrameData(texture, Math.max(1, duration)));
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        if (remainingTicks <= 0) {
            if (!frameQueue.isEmpty()) {
                currentFrame = frameQueue.poll();
                remainingTicks = currentFrame.duration;
            } else {
                currentFrame = null;
            }
        } else {
            remainingTicks--;
        }
    }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.HELMET.type()) return;
        if (currentFrame == null || remainingTicks <= 0) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        GuiGraphics guiGraphics = event.getGuiGraphics();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        guiGraphics.blit(
            currentFrame.texture,
            0, 0,
            screenWidth, screenHeight,
            0, 0,
            256, 256,
            256, 256
        );

        RenderSystem.disableBlend();
    }
}