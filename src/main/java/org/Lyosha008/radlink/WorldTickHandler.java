package org.Lyosha008.radlink;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

// Наши новые импорты для телепорта
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.UUID;

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

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            // Вызываем тикер телепорта каждую секунду без привязки к твоим скриптам
            tickTeleportQueue(server);
        }

        // ТВОЙ ОРИГИНАЛЬНЫЙ КОД НИЖЕ (БЕЗ ИЗМЕНЕНИЙ)
        normalTickCounter++;
        superTickCounter++;

        boolean runNormal = normalTickCounter >= 20;
        boolean runSuper = superTickCounter >= 10;

        if (!runNormal && !runSuper) return;

        if (runNormal) normalTickCounter = 0;
        if (runSuper) superTickCounter = 0;

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

    // --- ЛОГИКА ОЧЕРЕДИ ТЕЛЕПОРТА (ПРОСТО ДОПИСАНА В КОНЕЦ КЛАССА) ---
    private static class ReturnData {
        UUID entityUUID;
        ResourceKey<Level> targetDimension;
        double x, y, z;
        float yaw, pitch;
        int remainingTicks;

        ReturnData(UUID uuid, ResourceKey<Level> dim, double x, double y, double z, float yaw, float pitch, int ticks) {
            this.entityUUID = uuid;
            this.targetDimension = dim;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
            this.remainingTicks = ticks;
        }
    }

    private static final List<ReturnData> teleportQueue = new ArrayList<>();

    public static void scheduleReturn(UUID uuid, ResourceKey<Level> dim, double x, double y, double z, float yaw, float pitch, int ticks) {
        teleportQueue.add(new ReturnData(uuid, dim, x, y, z, yaw, pitch, ticks));
    }

    private static void tickTeleportQueue(MinecraftServer server) {
        if (teleportQueue.isEmpty()) return;

        Iterator<ReturnData> iterator = teleportQueue.iterator();
        while (iterator.hasNext()) {
            ReturnData data = iterator.next();
            data.remainingTicks--;

            if (data.remainingTicks <= 0) {
                Entity targetEntity = null;
                ServerPlayer player = server.getPlayerList().getPlayer(data.entityUUID);

                if (player != null) {
                    targetEntity = player;
                } else {
                    for (ServerLevel level : server.getAllLevels()) {
                        Entity found = level.getEntity(data.entityUUID);
                        if (found != null) {
                            targetEntity = found;
                            break;
                        }
                    }
                }

                if (targetEntity != null && targetEntity.isAlive()) {
                    ServerLevel targetWorld = server.getLevel(data.targetDimension);
                    if (targetWorld != null) {
                        BlockPos pos = new BlockPos((int) data.x, (int) data.y, (int) data.z);
                        targetWorld.setChunkForced(targetWorld.getChunkAt(pos).getPos().x, targetWorld.getChunkAt(pos).getPos().z, true);

                        if (targetEntity instanceof ServerPlayer serverPlayer) {
                            serverPlayer.teleportTo(targetWorld, data.x, data.y, data.z, data.yaw, data.pitch);
                        } else {
                            if (targetEntity.level() != targetWorld) {
                                targetEntity.changeDimension(targetWorld);
                            }
                            targetEntity.moveTo(data.x, data.y, data.z, data.yaw, data.pitch);
                        }

                        targetWorld.setChunkForced(targetWorld.getChunkAt(pos).getPos().x, targetWorld.getChunkAt(pos).getPos().z, false);
                    }
                }
                iterator.remove();
            }
        }
    }
}