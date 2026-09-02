package org.RadLink.radlink;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class ScriptSetHandler {

    public static void processSetAction(JsonArray setArray, ServerLevel level, BlockPos basePos, Entity entity) {
        for (JsonElement element : setArray) {
            if (!element.isJsonObject()) continue;
            JsonObject setObj = element.getAsJsonObject();

            String modeType = "";
            BlockPos targetPos = (entity != null) ? entity.blockPosition() : basePos;

            if (setObj.has("mode") && setObj.get("mode").isJsonArray()) {
                JsonArray modeArray = setObj.getAsJsonArray("mode");
                for (JsonElement mElem : modeArray) {
                    if (!mElem.isJsonObject()) continue;
                    JsonObject mObj = mElem.getAsJsonObject();

                    if (mObj.has("mode_type")) {
                        modeType = mObj.get("mode_type").getAsString();
                    }

                    if (mObj.has("coordinates_1")) {
                        targetPos = parseCustomCoords(mObj.getAsJsonArray("coordinates_1"), targetPos);
                    }
                }
            }

            boolean testsPassed = true;
            boolean shouldDestroy = false;

            if (setObj.has("tests") && setObj.get("tests").isJsonArray()) {
                for (JsonElement tElem : setObj.getAsJsonArray("tests")) {
                    if (!tElem.isJsonObject()) continue;
                    JsonObject testObj = tElem.getAsJsonObject();

                    if (testObj.has("isAir") && testObj.get("isAir").getAsBoolean()) {
                        if (!level.isEmptyBlock(targetPos)) {
                            testsPassed = false;
                            break;
                        }
                    }

                    if (testObj.has("type") && "destroy".equalsIgnoreCase(testObj.get("type").getAsString())) {
                        shouldDestroy = true;
                    }
                }
            }

            if (!testsPassed) continue;

            if ("set_block".equalsIgnoreCase(modeType)) {
                if (shouldDestroy) {
                    level.destroyBlock(targetPos, true);
                }

                if (setObj.has("block")) {
                    String blockId = setObj.get("block").getAsString();
                    Block block = BuiltInRegistries.BLOCK.get(new ResourceLocation(blockId));
                    if (block != Blocks.AIR || blockId.equals("minecraft:air")) {
                        level.setBlock(targetPos, block.defaultBlockState(), 3);
                    }
                }
            }
        }
    }

    private static BlockPos parseCustomCoords(JsonArray coordsArray, BlockPos origin) {
        if (coordsArray == null || coordsArray.size() < 3 || origin == null) return origin;

        int x = parseCoordElement(coordsArray.get(0), origin.getX());
        int y = parseCoordElement(coordsArray.get(1), origin.getY());
        int z = parseCoordElement(coordsArray.get(2), origin.getZ());

        return new BlockPos(x, y, z);
    }

    private static int parseCoordElement(JsonElement elem, int baseVal) {
        if (elem.isJsonPrimitive()) {
            String val = elem.getAsString();
            if ("~".equals(val)) return baseVal;
            try {
                return Integer.parseInt(val);
            } catch (NumberFormatException e) {
                return baseVal;
            }
        } else if (elem.isJsonObject()) {
            JsonObject obj = elem.getAsJsonObject();
            if (obj.has("~")) {
                try {
                    return baseVal + Integer.parseInt(obj.get("~").getAsString());
                } catch (NumberFormatException e) {
                    return baseVal;
                }
            }
        }
        return baseVal;
    }
}