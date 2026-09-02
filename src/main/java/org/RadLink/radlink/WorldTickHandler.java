package org.RadLink.radlink;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Mod.EventBusSubscriber(modid = Radlink.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class WorldTickHandler {

    private static int normalTickCounter = 0;
    private static int superTickCounter = 0;

    @SubscribeEvent
    public static void onEntityJoin(net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;

        if (event.getEntity() instanceof net.minecraft.world.entity.LightningBolt lightning) {
            ServerLevel level = (ServerLevel) event.getLevel();

            Path worldRoot = Radlink.resolveWorldDataDir();
            if (worldRoot == null) return;

            List<CustomDefinitions.CustomScriptDefinition> scripts = CustomDefinitions.loadScripts(worldRoot);
            for (CustomDefinitions.CustomScriptDefinition script : scripts) {
                if (script.isSuper()) {
                    ScriptEngine.processWorldTick(level, script);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        normalTickCounter++;
        superTickCounter++;

        boolean runNormal = normalTickCounter >= 20;
        boolean runSuper = superTickCounter >= 10;

        if (!runNormal && !runSuper) return;

        if (runNormal) normalTickCounter = 0;
        if (runSuper) superTickCounter = 0;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        Path worldRoot = Radlink.resolveWorldDataDir();
        if (worldRoot == null) return;

        Path globalJsonPath = worldRoot.resolve("global.json");
        if (!Files.exists(globalJsonPath)) return;

        try {
            List<CustomDefinitions.CustomScriptDefinition> scripts = CustomDefinitions.loadScripts(worldRoot);
            if (scripts.isEmpty()) return;

            for (ServerLevel level : server.getAllLevels()) {
                for (CustomDefinitions.CustomScriptDefinition script : scripts) {
                    for (CustomDefinitions.CustomScriptDefinition scriptDef : scripts) {
                        boolean isSuper = scriptDef.isSuper();

                        if ((isSuper && runSuper) || (!isSuper && runNormal)) {
                            ScriptEngine.processWorldTick(level, scriptDef);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}