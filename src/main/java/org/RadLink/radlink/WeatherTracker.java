package org.RadLink.radlink;

import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Radlink.MOD_ID)
public class WeatherTracker {

    private static boolean wasRaining = false;

    @SubscribeEvent
    public static void onWorldTick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel level) {
            boolean isRaining = level.isRaining();

            // Если дождь только что начался
            if (isRaining && !wasRaining) {
                ScriptEngine.rainStartTick = level.getGameTime();
            }
            // Если дождь закончился — сбрасываем
            else if (!isRaining && wasRaining) {
                ScriptEngine.rainStartTick = -1;
            }

            wasRaining = isRaining;
        }
    }
}