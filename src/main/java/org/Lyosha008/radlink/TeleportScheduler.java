package org.Lyosha008.radlink;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;

public class TeleportScheduler {

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

    private static final List<ReturnData> queue = new ArrayList<>();

    public static void scheduleReturn(UUID uuid,ResourceKey<Level>dim,double x,double y,double z,float yaw,float pitch,int ticks){
        queue.add(new ReturnData(uuid, dim, x, y, z, yaw, pitch, ticks));
    }

    public static void onServerTick(MinecraftServer server) {
        if (queue.isEmpty()) return;

        Iterator<ReturnData> iterator = queue.iterator();
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
