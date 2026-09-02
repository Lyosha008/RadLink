package org.AtomLink.radlink;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;

public class ScriptEngine {
    private static boolean isNewDayThisTick = false;
    private static long lastProcessedTick = -1;
    public static long rainStartTick = -1;

    public static boolean isSuperScript(JsonObject json) {
        if (json == null) return false;
        if (json.has("super") && json.get("super").getAsBoolean()) return true;
        if (json.has("if") && json.get("if").isJsonObject()) {
            JsonObject ifObj = json.getAsJsonObject("if");
            return ifObj.has("super") && ifObj.get("super").getAsBoolean();
        }
        return false;
    }

    public static String formatTextWithVariables(String text, ServerLevel level, BlockPos pos, Entity entity) {
        if (text == null || !text.contains("$")) return text;

        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\$[{\\(]([^}\\)]+)[}\\)]");
        java.util.regex.Matcher matcher = pattern.matcher(text);

        Map<String, Object> vars = Radlink.loadWorldVariables();
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            String rawVarName = matcher.group(1);
            String resolvedKey = resolveVarName(rawVarName, level, pos, entity);

            Object val = vars.get(resolvedKey);

            if (val == null) {
                if (vars.containsKey("global_" + rawVarName)) {
                    val = vars.get("global_" + rawVarName);
                } else if (vars.containsKey(rawVarName)) {
                    val = vars.get(rawVarName);
                } else {
                    val = 0.0;
                }
            }

            matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(String.valueOf(val)));
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    public static String formatTextWithVariables(String text, ServerLevel level, BlockPos pos) {
        return formatTextWithVariables(text, level, pos, null);
    }

    public static void clearBlockVariables(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return;

        Map<String, Object> vars = Radlink.loadWorldVariables();
        String posSuffix = "_" + level.dimension().location().getPath() + "_" + pos.getX() + "_" + pos.getY() + "_" + pos.getZ();

        boolean changed = vars.keySet().removeIf(key -> key.endsWith(posSuffix));

        if (changed) {
            Radlink.saveWorldVariables(vars);
        }
    }

    public static void processWorldTick(ServerLevel level, CustomDefinitions.CustomScriptDefinition script) {
        JsonObject ifObj = script.ifCondition();
        if (ifObj == null) return;

        String who = ifObj.has("who") ? ifObj.get("who").getAsString() : "block";

        if ("entity".equalsIgnoreCase(who)) {
            for (Entity entity : level.getAllEntities()) {
                if (checkConditions(ifObj, null, level, entity.blockPosition(), entity)) {
                    executeActions(script.thenActions(), level, entity.blockPosition(), entity);
                }
            }
            return;
        }

        String targetBlockId = null;
        if (ifObj.has("block") && ifObj.get("block").isJsonObject()) {
            JsonObject blockCheck = ifObj.getAsJsonObject("block");
            if (blockCheck.has("==")) {
                targetBlockId = blockCheck.get("==").getAsString();
            }
        }

        if (targetBlockId == null) return;

        for (ServerPlayer player : level.players()) {
            ChunkPos playerChunk = player.chunkPosition();
            int radius = 2;

            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    LevelChunk chunk = level.getChunkSource().getChunkNow(playerChunk.x + x, playerChunk.z + z);
                    if (chunk == null) continue;

                    for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
                        BlockPos pos = entry.getKey();
                        BlockState state = chunk.getBlockState(pos);
                        ResourceLocation loc = BuiltInRegistries.BLOCK.getKey(state.getBlock());

                        if (loc.toString().equalsIgnoreCase(targetBlockId) || loc.getPath().equalsIgnoreCase(targetBlockId)) {
                            if (checkConditions(ifObj, state, level, pos, null)) {
                                executeActions(script.thenActions(), level, pos, null);
                            }
                        }
                    }
                }
            }
        }
    }

    public static boolean checkConditions(JsonObject ifObj,BlockState state,ServerLevel level,BlockPos pos,Entity entity) {
        if (ifObj.has("timeCycles") && ifObj.get("timeCycles").isJsonObject()) {
            JsonObject tc = ifObj.getAsJsonObject("timeCycles");
            if (tc.has("now") && "isNewDay".equalsIgnoreCase(tc.get("now").getAsString())) {
                if (!isNewDay(level)) return false;
            }
        }

        if (ifObj.has("lit") && state != null) {
            boolean expectedLit = ifObj.get("lit").getAsBoolean();
            boolean actualLit = state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT);
            if (expectedLit != actualLit) return false;
        }

        if (ifObj.has("item") && entity instanceof ServerPlayer player) {
            JsonElement itemElem = ifObj.get("item");
            String heldItemId = BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()).toString();

            if (itemElem.isJsonObject()) {
                JsonObject itemObj = itemElem.getAsJsonObject();
                if (itemObj.has("==") && !heldItemId.equalsIgnoreCase(itemObj.get("==").getAsString())) {
                    return false;
                }
                if (itemObj.has("!=") && heldItemId.equalsIgnoreCase(itemObj.get("!=").getAsString())) {
                    return false;
                }
            } else if (itemElem.isJsonPrimitive()) {
                if (!heldItemId.equalsIgnoreCase(itemElem.getAsString())) {
                    return false;
                }
            }
        }

        if (ifObj.has("calc") && ifObj.get("calc").isJsonObject()) {
            JsonObject calcObj = ifObj.getAsJsonObject("calc");
            if (calcObj.has("expression")) {
                String rawExpr = calcObj.get("expression").getAsString();
                String formattedExpr = formatTextWithVariables(rawExpr, level, pos, entity);

                double calcValue = evaluateMath(formattedExpr);

                if (!evalComparison(calcValue, calcObj, level, pos, entity)) {
                    return false;
                }
            }
        }

        if (ifObj.has("weather")) {
            String expectedWeather = ifObj.get("weather").getAsString();
            boolean isRaining = level.isRaining();
            boolean isThundering = level.isThundering();

            if ("clear".equalsIgnoreCase(expectedWeather) && (isRaining || isThundering)) {
                return false;
            }
            if ("rain".equalsIgnoreCase(expectedWeather) && (!isRaining || isThundering)) {
                return false;
            }
            if ("thunder".equalsIgnoreCase(expectedWeather) && !isThundering) {
                return false;
            }
        }

        if (ifObj.has("time_check") && ifObj.get("time_check").isJsonObject()) {
            JsonObject timeObj = ifObj.getAsJsonObject("time_check");

            if (timeObj.has("ago") && timeObj.has("time")) {
                String unit = timeObj.get("ago").getAsString(); // "second" или "tick"
                long requiredTime = timeObj.get("time").getAsLong();
                long requiredTicks = "second".equalsIgnoreCase(unit) ? requiredTime * 20 : requiredTime;

                // Проверяем, идет ли дождь и записан ли старт
                if (level.isRaining() && rainStartTick != -1) {
                    long currentRainDuration = level.getGameTime() - rainStartTick;

                    // Если дождь идет МЕНЬШЕ нужного времени — условие НЕ проходит!
                    if (currentRainDuration < requiredTicks) {
                        return false;
                    }
                } else {
                    // Если дождь вообще не идет
                    return false;
                }
            }
        }

        if (state != null) {
            for (Map.Entry<String, JsonElement> entry : ifObj.entrySet()) {
                String key = entry.getKey();
                if (key.equalsIgnoreCase("block") || key.equalsIgnoreCase("who") || key.equalsIgnoreCase("If") || key.equalsIgnoreCase("iF") || key.equalsIgnoreCase("timeCycles") || key.equalsIgnoreCase("query") || key.equalsIgnoreCase("lit")) continue;

                Property<?> property = state.getBlock().getStateDefinition().getProperty(key);
                if (property != null) {
                    String actualVal = state.getValue(property).toString();
                    if (entry.getValue().isJsonObject()) {
                        JsonObject compObj = entry.getValue().getAsJsonObject();
                        if (compObj.has("==") && !actualVal.equalsIgnoreCase(compObj.get("==").getAsString())) return false;
                        if (compObj.has("!=") && actualVal.equalsIgnoreCase(compObj.get("!=") .getAsString())) return false;
                    } else {
                        String expectedVal = entry.getValue().getAsString();
                        if (!actualVal.equalsIgnoreCase(expectedVal)) {
                            return false;
                        }
                    }
                }
            }
        }

        JsonObject nestedIf = null;
        if (ifObj.has("If") && ifObj.get("If").isJsonObject()) {
            nestedIf = ifObj.getAsJsonObject("If");
        } else if (ifObj.has("iF") && ifObj.get("iF").isJsonObject()) {
            nestedIf = ifObj.getAsJsonObject("iF");
        }

        if (nestedIf != null) {
            if (!checkNestedCondition(nestedIf, level, pos, entity)) {
                return false;
            }
        }

        return true;
    }

    public static void executeActions(JsonElement thenElement, ServerLevel level, BlockPos pos) {
        executeActions(thenElement, level, pos, null);
    }

    public static void executeActions(JsonElement thenElement, ServerLevel level, BlockPos pos, Entity entity) {
        if (thenElement == null) return;

        if (thenElement.isJsonArray()) {
            JsonArray array = thenElement.getAsJsonArray();
            for (JsonElement elem : array) {
                if (elem.isJsonObject()) executeSingleAction(elem.getAsJsonObject(), level, pos, entity);
            }
        } else if (thenElement.isJsonObject()) {
            executeSingleAction(thenElement.getAsJsonObject(), level, pos, entity);
        }
    }

    private static void executeSingleAction(JsonObject thenObj, ServerLevel level, BlockPos pos, Entity entity) {
        JsonObject ifPart = null;
        if (thenObj.has("If") && thenObj.get("If").isJsonObject()) ifPart = thenObj.getAsJsonObject("If");
        else if (thenObj.has("iF") && thenObj.get("iF").isJsonObject()) ifPart = thenObj.getAsJsonObject("iF");

        if (ifPart != null && thenObj.has("Then")) {
            if (checkNestedCondition(ifPart, level, pos, entity)) {
                executeActions(thenObj.get("Then"), level, pos, entity);
            }
            return;
        }

        if (thenObj.has("case") && thenObj.get("case").isJsonArray()) {
            JsonArray caseArray = thenObj.getAsJsonArray("case");
            for (JsonElement elem : caseArray) {
                if (!elem.isJsonObject()) continue;
                JsonObject caseItem = elem.getAsJsonObject();

                JsonObject caseIf = null;
                if (caseItem.has("If") && caseItem.get("If").isJsonObject()) caseIf = caseItem.getAsJsonObject("If");
                else if (caseItem.has("iF") && caseItem.get("iF").isJsonObject()) caseIf = caseItem.getAsJsonObject("iF");

                if (caseIf != null && caseItem.has("Then")) {
                    if (checkNestedCondition(caseIf, level, pos, entity)) {
                        executeActions(caseItem.get("Then"), level, pos, entity);
                        break;
                    }
                }
            }
        }

        if (thenObj.has("add") && thenObj.get("add").isJsonObject()) {
            JsonObject addObj = thenObj.getAsJsonObject("add");
            Map<String, Object> vars = Radlink.loadWorldVariables();

            boolean changed = false;
            for (Map.Entry<String, JsonElement> entry : addObj.entrySet()) {
                String varName = resolveVarName(entry.getKey(), level, pos, entity);
                double amount = parseValueOrVar(entry.getValue(), level, pos, entity);

                double currentValue = parseDouble(vars.getOrDefault(varName, 0.0));
                double newValue = currentValue + amount;

                if (newValue <= 0) {
                    vars.remove(varName);
                } else {
                    vars.put(varName, newValue);
                }

                changed = true;
            }
            if (changed) {
                Radlink.saveWorldVariables(vars);
            }
        }

        if (thenObj.has("set")) {
            JsonElement setElem = thenObj.get("set");

            if (setElem.isJsonArray()) {
                JsonArray setArray = setElem.getAsJsonArray();
                for (JsonElement itemElem : setArray) {
                    if (itemElem.isJsonObject()) {
                        executeSetBlockAction(itemElem.getAsJsonObject(), level, pos, entity);
                    }
                }
            } else if (setElem.isJsonObject()) {
                JsonObject setObj = setElem.getAsJsonObject();
                Map<String, Object> vars = Radlink.loadWorldVariables();

                boolean changed = false;
                for (Map.Entry<String, JsonElement> entry : setObj.entrySet()) {
                    String varName = resolveVarName(entry.getKey(), level, pos, entity);
                    double newValue = parseValueOrVar(entry.getValue(), level, pos, entity);
                    vars.put(varName, newValue);
                    changed = true;
                }
                if (changed) {
                    Radlink.saveWorldVariables(vars);
                }
            }
        }

        if (thenObj.has("sound") && thenObj.get("sound").isJsonObject()) {
            JsonObject soundObj = thenObj.getAsJsonObject("sound");
            String soundId = soundObj.get("id").getAsString();
            float volume = soundObj.has("volume") ? soundObj.get("volume").getAsFloat() : 1.0f;
            float pitch = soundObj.has("pitch") ? soundObj.get("pitch").getAsFloat() : 1.0f;

            ResourceLocation soundLoc = ResourceLocation.tryParse(soundId);
            SoundEvent soundEvent = soundLoc != null ? ForgeRegistries.SOUND_EVENTS.getValue(soundLoc) : null;

            if (soundEvent != null && level != null) {
                if (entity != null) {
                    level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), soundEvent, SoundSource.PLAYERS, volume, pitch);
                } else if (pos != null) {
                    level.playSound(null, pos, soundEvent, SoundSource.BLOCKS, volume, pitch);
                }
            }
        }

        if (thenObj.has("run")) {
            executeActions(thenObj.get("run"), level, pos, entity);
        }

        if (thenObj.has("command")) {
            String commandText = thenObj.get("command").getAsString();

            if (entity != null) {
                commandText = commandText.replace("${player}", entity.getName().getString())
                    .replace("$player", entity.getName().getString());
            }

            commandText = formatTextWithVariables(commandText, level, pos, entity);

            if (level != null) {
                CommandSourceStack source = level.getServer().createCommandSourceStack()
                    .withPermission(4)
                    .withSuppressedOutput();

                if (entity != null) {
                    source = source.withEntity(entity).withPosition(entity.position());
                } else if (pos != null) {
                    source = source.withPosition(Vec3.atCenterOf(pos));
                }

                level.getServer().getCommands().performPrefixedCommand(source, commandText);
            }
        }

        if (thenObj.has("damage") && entity != null) {
            entity.hurt(level.damageSources().magic(), thenObj.get("damage").getAsFloat());
        }

        if (thenObj.has("say") && thenObj.get("say").isJsonObject()) {
            JsonObject sayObj = thenObj.getAsJsonObject("say");
            if (sayObj.has("text")) {
                String rawText = sayObj.get("text").getAsString();
                String formattedText = formatTextWithVariables(rawText, level, pos, entity);

                for (ServerPlayer player : level.players()) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(formattedText));
                }
            }
        }

        if (thenObj.has("explosion")) {
            JsonObject expObj = thenObj.getAsJsonObject("explosion");
            float radius = expObj.has("radius") ? expObj.get("radius").getAsFloat() : 3.0f;
            boolean causeFire = expObj.has("fire") && expObj.get("fire").getAsBoolean();

            BlockPos targetPos = (entity != null) ? entity.blockPosition() : pos;
            if (targetPos != null && level != null) {
                level.explode(
                    null,
                    targetPos.getX() + 0.5,
                    targetPos.getY() + 0.5,
                    targetPos.getZ() + 0.5,
                    radius,
                    causeFire,
                    Level.ExplosionInteraction.BLOCK
                );
            }
        }

        if (thenObj.has("summon") && entity instanceof LivingEntity livingEntity) {
            JsonObject summonObj = thenObj.getAsJsonObject("summon");
            String entityTypeId = summonObj.has("type") ? summonObj.get("type").getAsString() : (summonObj.has("id") ? summonObj.get("id").getAsString() : "");

            var entityType = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(entityTypeId));
            if (entityType != null && livingEntity.level() instanceof ServerLevel serverLevel) {
                int count = summonObj.has("count") ? summonObj.get("count").getAsInt() : 1;

                for (int i = 0; i < count; i++) {
                    Entity spawned = entityType.create(serverLevel);
                    if (spawned != null) {
                        spawned.moveTo(livingEntity.getX(), livingEntity.getY(), livingEntity.getZ(), livingEntity.getYRot(), livingEntity.getXRot());
                        serverLevel.addFreshEntity(spawned);
                    }
                }
            }
        }

        if (thenObj.has("effect") && entity instanceof LivingEntity livingEntity) {
            JsonObject eff = thenObj.getAsJsonObject("effect");
            String effectId = eff.has("type") ? eff.get("type").getAsString() : (eff.has("id") ? eff.get("id").getAsString() : "");
            var effect = ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation(effectId));

            if (effect != null) {
                int dur = eff.has("duration") ? eff.get("duration").getAsInt() : 200;
                int amp = eff.has("amplifier") ? eff.get("amplifier").getAsInt() : 0;
                livingEntity.addEffect(new MobEffectInstance(effect, dur, amp));
            }
        }

        if (thenObj.has("particle") && thenObj.get("particle").isJsonObject()) {
            JsonObject particleObj = thenObj.getAsJsonObject("particle");

            String typeStr = particleObj.has("type") ? particleObj.get("type").getAsString() : "minecraft:smoke";
            int count = particleObj.has("count") ? particleObj.get("count").getAsInt() : 1;
            double speed = particleObj.has("speed") ? particleObj.get("speed").getAsDouble() : 0.1;

            double offX = particleObj.has("offset_x") ? particleObj.get("offset_x").getAsDouble() : (particleObj.has("x") ? particleObj.get("x").getAsDouble() : 0.5);
            double offY = particleObj.has("offset_y") ? particleObj.get("offset_y").getAsDouble() : (particleObj.has("y") ? particleObj.get("y").getAsDouble() : 1.0);
            double offZ = particleObj.has("offset_z") ? particleObj.get("offset_z").getAsDouble() : (particleObj.has("z") ? particleObj.get("z").getAsDouble() : 0.5);

            double defaultSpread = particleObj.has("spread") ? particleObj.get("spread").getAsDouble() : 0.1;
            double deltaX = particleObj.has("delta_x") ? particleObj.get("delta_x").getAsDouble() : defaultSpread;
            double deltaY = particleObj.has("delta_y") ? particleObj.get("delta_y").getAsDouble() : defaultSpread;
            double deltaZ = particleObj.has("delta_z") ? particleObj.get("delta_z").getAsDouble() : defaultSpread;

            ResourceLocation loc = ResourceLocation.tryParse(typeStr);
            if (loc != null) {
                ParticleType<?> particleType = BuiltInRegistries.PARTICLE_TYPE.get(loc);
                if (particleType instanceof ParticleOptions options && pos != null) {
                    level.sendParticles(
                        options,
                        pos.getX() + offX,
                        pos.getY() + offY,
                        pos.getZ() + offZ,
                        count,
                        deltaX, deltaY, deltaZ,
                        speed
                    );
                }
            }
        }

        if (thenObj.has("message")) {
            String rawText = thenObj.get("message").getAsString();
            String formattedText = formatTextWithVariables(rawText, level, pos, entity);

            for (ServerPlayer player : level.players()) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(formattedText));
            }
        }
    }

    private static void executeSetBlockAction(JsonObject setObj, ServerLevel level, BlockPos basePos, Entity entity) {
        String modeType = "set_block";
        BlockPos originPos = (entity != null) ? entity.blockPosition() : basePos;
        BlockPos pos1 = originPos;
        BlockPos pos2 = originPos;

        if (setObj.has("mode") && setObj.get("mode").isJsonArray()) {
            for (JsonElement mElem : setObj.getAsJsonArray("mode")) {
                if (!mElem.isJsonObject()) continue;
                JsonObject mObj = mElem.getAsJsonObject();

                if (mObj.has("mode_type")) modeType = mObj.get("mode_type").getAsString();
                if (mObj.has("coordinates_1") && mObj.get("coordinates_1").isJsonArray()) {
                    pos1 = parseCustomCoords(mObj.getAsJsonArray("coordinates_1"), originPos);
                    pos2 = pos1;
                }
                if (mObj.has("coordinates_2") && mObj.get("coordinates_2").isJsonArray()) {
                    pos2 = parseCustomCoords(mObj.getAsJsonArray("coordinates_2"), originPos);
                }
            }
        }

        boolean checkAir = false;
        boolean isAirValue = false;
        String actionType = "replace";
        String filterBlockId = null;

        if (setObj.has("tests") && setObj.get("tests").isJsonArray()) {
            for (JsonElement tElem : setObj.getAsJsonArray("tests")) {
                if (!tElem.isJsonObject()) continue;
                JsonObject testObj = tElem.getAsJsonObject();

                if (testObj.has("isAir")) {
                    checkAir = true;
                    isAirValue = testObj.get("isAir").getAsBoolean();
                }
                if (testObj.has("type")) {
                    actionType = testObj.get("type").getAsString().toLowerCase();
                }
                if (testObj.has("filter")) {
                    filterBlockId = testObj.get("filter").getAsString();
                }
            }
        }

        if (setObj.has("block") && level != null) {
            String blockId = setObj.get("block").getAsString();
            ResourceLocation loc = blockId.contains(":") ? new ResourceLocation(blockId) : new ResourceLocation("minecraft", blockId);
            Block newBlock = BuiltInRegistries.BLOCK.get(loc);

            if (newBlock != null) {
                int minX = Math.min(pos1.getX(), pos2.getX());
                int maxX = Math.max(pos1.getX(), pos2.getX());
                int minY = Math.min(pos1.getY(), pos2.getY());
                int maxY = Math.max(pos1.getY(), pos2.getY());
                int minZ = Math.min(pos1.getZ(), pos2.getZ());
                int maxZ = Math.max(pos1.getZ(), pos2.getZ());

                for (int x = minX; x <= maxX; x++) {
                    for (int y = minY; y <= maxY; y++) {
                        for (int z = minZ; z <= maxZ; z++) {
                            BlockPos currentPos = new BlockPos(x, y, z);
                            BlockState currentState = level.getBlockState(currentPos);

                            if (checkAir && level.isEmptyBlock(currentPos) != isAirValue) {
                                continue;
                            }

                            if (filterBlockId != null && !filterBlockId.isEmpty()) {
                                ResourceLocation currentLoc = BuiltInRegistries.BLOCK.getKey(currentState.getBlock());
                                String currentPath = currentLoc.getPath();
                                String currentFull = currentLoc.toString();

                                if (!currentFull.equalsIgnoreCase(filterBlockId) && !currentPath.equalsIgnoreCase(filterBlockId)) {
                                    continue;
                                }
                            }

                            if ("destroy".equals(actionType)) {
                                level.destroyBlock(currentPos, true);
                            } else if ("keep".equals(actionType)) {
                                if (!level.isEmptyBlock(currentPos)) {
                                    continue;
                                }
                            }

                            level.setBlock(currentPos, newBlock.defaultBlockState(), 3);
                        }
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

    public static boolean isNewDay(ServerLevel level) {
        if (level == null) return false;

        long currentTick = level.getGameTime();

        if (currentTick == lastProcessedTick) {
            return isNewDayThisTick;
        }

        lastProcessedTick = currentTick;
        long currentDay = level.getDayTime() / 24000L;

        Map<String, Object> vars = Radlink.loadWorldVariables();
        long lastDay = parseLong(vars.get("global_last_day"), -1L);

        if (lastDay == -1L) {
            vars.put("global_last_day", (double) currentDay);
            Radlink.saveWorldVariables(vars);
            isNewDayThisTick = false;
            return false;
        }

        if (currentDay > lastDay) {
            vars.put("global_last_day", (double) currentDay);
            Radlink.saveWorldVariables(vars);
            isNewDayThisTick = true;
            return true;
        }

        isNewDayThisTick = false;
        return false;
    }

    private static long parseLong(Object obj, long defaultValue) {
        if (obj instanceof Number num) return num.longValue();
        if (obj != null) {
            try {
                return Long.parseLong(obj.toString().split("\\.")[0]);
            } catch (Exception ignored) {}
        }
        return defaultValue;
    }

    private static boolean checkNestedCondition(JsonObject varCheck, ServerLevel level, BlockPos pos, Entity entity) {
        if (varCheck.has("timeCycles") && varCheck.get("timeCycles").isJsonObject()) {
            JsonObject tc = varCheck.getAsJsonObject("timeCycles");
            if (tc.has("now") && "isNewDay".equalsIgnoreCase(tc.get("now").getAsString())) {
                if (!isNewDay(level)) {
                    return false;
                }
            }
        }

        if (varCheck.has("entity")) {
            JsonElement entElem = varCheck.get("entity");
            String actualEntityType = (entity != null) ? EntityType.getKey(entity.getType()).getPath() : "";
            String actualEntityFull = (entity != null) ? EntityType.getKey(entity.getType()).toString() : "";

            if (entElem.isJsonObject()) {
                JsonObject entObj = entElem.getAsJsonObject();
                if (entObj.has("==")) {
                    String target = entObj.get("==").getAsString();
                    if (!actualEntityType.equalsIgnoreCase(target) && !actualEntityFull.equalsIgnoreCase(target)) {
                        return false;
                    }
                }
                if (entObj.has("!=")) {
                    String target = entObj.get("!=").getAsString();
                    if (actualEntityType.equalsIgnoreCase(target) || actualEntityFull.equalsIgnoreCase(target)) {
                        return false;
                    }
                }
            }
        }

        if (varCheck.has("block")) {
            JsonElement blockElem = varCheck.get("block");

            if (blockElem.isJsonObject()) {
                JsonObject blockCheck = blockElem.getAsJsonObject();
                BlockPos targetPos = (entity != null) ? entity.blockPosition() : pos;

                if (blockCheck.has("coordinates_1") && blockCheck.get("coordinates_1").isJsonArray()) {
                    targetPos = parseCustomCoords(blockCheck.getAsJsonArray("coordinates_1"), targetPos);
                }

                if (targetPos != null && level != null) {
                    String expected = null;
                    if (blockCheck.has("this")) expected = blockCheck.get("this").getAsString();
                    else if (blockCheck.has("==")) expected = blockCheck.get("==").getAsString();

                    if (expected != null) {
                        boolean isLightning = (entity != null && "lightning_bolt".equalsIgnoreCase(EntityType.getKey(entity.getType()).getPath()));

                        BlockState state = level.getBlockState(targetPos);
                        ResourceLocation loc = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                        boolean matches = loc.getPath().equalsIgnoreCase(expected) || loc.toString().equalsIgnoreCase(expected);

                        if (!matches && isLightning) {
                            BlockState stateBelow = level.getBlockState(targetPos.below());
                            ResourceLocation locBelow = BuiltInRegistries.BLOCK.getKey(stateBelow.getBlock());
                            matches = locBelow.getPath().equalsIgnoreCase(expected) || locBelow.toString().equalsIgnoreCase(expected);

                            if (!matches) {
                                BlockState stateAbove = level.getBlockState(targetPos.above());
                                ResourceLocation locAbove = BuiltInRegistries.BLOCK.getKey(stateAbove.getBlock());
                                matches = locAbove.getPath().equalsIgnoreCase(expected) || locAbove.toString().equalsIgnoreCase(expected);
                            }
                        }

                        if (!matches) {
                            return false;
                        }
                    }
                }
            } else if (blockElem.isJsonPrimitive()) {
                String varName = cleanVarName(resolveVarName(blockElem.getAsString(), level, pos, entity));
                Map<String, Object> vars = Radlink.loadWorldVariables();
                double currentValue = parseDouble(vars.getOrDefault(varName, 0.0));

                if (!evalComparison(currentValue, varCheck, level, pos, entity)) {
                    return false;
                }
            }
        }

        if (varCheck.has("calc") && varCheck.get("calc").isJsonObject()) {
            JsonObject calcObj = varCheck.getAsJsonObject("calc");
            if (calcObj.has("expression")) {
                double calcValue = parseValueOrVar(calcObj.get("expression"), level, pos, entity);
                if (!evalComparison(calcValue, calcObj, level, pos, entity)) {
                    return false;
                }
            }
        }

        if (varCheck.has("query")) {
            JsonElement queryElem = varCheck.get("query");
            if (queryElem.isJsonObject()) {
                JsonObject queryObj = queryElem.getAsJsonObject();
                for (Map.Entry<String, JsonElement> entry : queryObj.entrySet()) {
                    String varName = cleanVarName(resolveVarName(entry.getKey(), level, pos, entity));
                    Map<String, Object> vars = Radlink.loadWorldVariables();
                    double currentValue = parseDouble(vars.getOrDefault(varName, 0.0));

                    if (!evalComparison(currentValue, entry.getValue(), level, pos, entity)) {
                        return false;
                    }
                }
            } else if (queryElem.isJsonArray()) {
                if (!evalArrayQuery(queryElem.getAsJsonArray(), level, pos, entity)) {
                    return false;
                }
            }
        }

        for (Map.Entry<String, JsonElement> entry : varCheck.entrySet()) {
            String key = entry.getKey();
            if (key.equalsIgnoreCase("entity") || key.equalsIgnoreCase("timeCycles") || key.equalsIgnoreCase("query") || key.equalsIgnoreCase("block") || key.equalsIgnoreCase("var")
                || key.equals(">") || key.equals(">=") || key.equals("<") || key.equals("<=") || key.equals("==") || key.equals("!=")) {
                continue;
            }

            String varName = cleanVarName(resolveVarName(key, level, pos, entity));
            Map<String, Object> vars = Radlink.loadWorldVariables();
            double currentValue = parseDouble(vars.getOrDefault(varName, 0.0));

            if (!evalComparison(currentValue, entry.getValue(), level, pos, entity)) {
                return false;
            }
        }

        String rawVar = null;
        if (varCheck.has("var")) rawVar = varCheck.get("var").getAsString();

        if (rawVar != null) {
            String varName = cleanVarName(resolveVarName(rawVar, level, pos, entity));
            Map<String, Object> vars = Radlink.loadWorldVariables();
            double currentValue = parseDouble(vars.getOrDefault(varName, 0.0));

            if (varCheck.has(">") && currentValue <= parseValueOrVar(varCheck.get(">"), level, pos, entity)) return false;
            if (varCheck.has(">=") && currentValue < parseValueOrVar(varCheck.get(">="), level, pos, entity)) return false;
            if (varCheck.has("<") && currentValue >= parseValueOrVar(varCheck.get("<"), level, pos, entity)) return false;
            if (varCheck.has("<=") && currentValue > parseValueOrVar(varCheck.get("<="), level, pos, entity)) return false;
            if (varCheck.has("==") && !evalComparison(currentValue, varCheck.get("=="), level, pos, entity)) return false;
            if (varCheck.has("!=") && evalComparison(currentValue, varCheck.get("!="), level, pos, entity)) return false;
        }

        return true;
    }

    private static String cleanVarName(String raw) {
        if (raw == null) return "";
        return raw.replace("${", "").replace("}", "").replace("$", "").trim();
    }

    private static boolean evalArrayQuery(JsonArray array, ServerLevel level, BlockPos pos, Entity entity) {
        if (array.size() < 2) return false;

        JsonElement firstElem = array.get(0);
        double evaluatedValue = 0.0;

        if (firstElem.isJsonObject() && firstElem.getAsJsonObject().has("random")) {
            JsonElement randElem = firstElem.getAsJsonObject().get("random");
            double from = 0.0;
            double to = 1.0;
            int decimals = -1;

            if (randElem.isJsonArray()) {
                for (JsonElement item : randElem.getAsJsonArray()) {
                    if (item.isJsonObject()) {
                        JsonObject obj = item.getAsJsonObject();
                        if (obj.has("from")) from = obj.get("from").getAsDouble();
                        if (obj.has("to")) to = obj.get("to").getAsDouble();
                        if (obj.has("numbers_after_comma")) decimals = obj.get("numbers_after_comma").getAsInt();
                    }
                }
            }

            evaluatedValue = from + Math.random() * (to - from);
            if (decimals >= 0) {
                double factor = Math.pow(10, decimals);
                evaluatedValue = Math.round(evaluatedValue * factor) / factor;
            }
        } else {
            evaluatedValue = parseValueOrVar(firstElem, level, pos, entity);
        }

        JsonElement secondElem = array.get(1);
        return evalComparison(evaluatedValue, secondElem, level, pos, entity);
    }

    private static boolean evalComparison(double val, JsonElement rule, ServerLevel level, BlockPos pos, Entity entity) {
        if (rule.isJsonArray()) {
            double from = Double.NEGATIVE_INFINITY;
            double to = Double.POSITIVE_INFINITY;
            for (JsonElement elem : rule.getAsJsonArray()) {
                if (elem.isJsonObject()) {
                    JsonObject obj = elem.getAsJsonObject();
                    if (obj.has("from")) from = parseValueOrVar(obj.get("from"), level, pos, entity);
                    if (obj.has("to")) to = parseValueOrVar(obj.get("to"), level, pos, entity);
                }
            }
            return val >= from && val <= to;
        } else if (rule.isJsonObject()) {
            JsonObject obj = rule.getAsJsonObject();
            if (obj.has(">=")) if (val < parseValueOrVar(obj.get(">="), level, pos, entity)) return false;
            if (obj.has(">")) if (val <= parseValueOrVar(obj.get(">"), level, pos, entity)) return false;
            if (obj.has("<=")) if (val > parseValueOrVar(obj.get("<="), level, pos, entity)) return false;
            if (obj.has("<")) if (val >= parseValueOrVar(obj.get("<"), level, pos, entity)) return false;
            if (obj.has("==")) {
                JsonElement eqElem = obj.get("==");
                if (eqElem.isJsonArray()) {
                    if (!evalComparison(val, eqElem, level, pos, entity)) return false;
                } else {
                    if (val != parseValueOrVar(eqElem, level, pos, entity)) return false;
                }
            }
            if (obj.has("!=")) if (val == parseValueOrVar(obj.get("!="), level, pos, entity)) return false;
            return true;
        } else {
            return val == parseValueOrVar(rule, level, pos, entity);
        }
    }


    public static String resolveVarName(String rawVar, Level level, BlockPos pos) {
        return resolveVarName(rawVar, level, pos, null);
    }

    public static String resolveVarName(String rawVar, Level level, BlockPos pos, Entity entity) {
        if (rawVar == null) return "";
        String varName = rawVar.replace("${", "").replace("}", "");

        if (varName.startsWith("global_")) {
            return varName;
        }

        if (entity != null) {
            return varName + "_" + entity.getUUID().toString();
        }

        if (level != null && pos != null) {
            return varName + "_" + level.dimension().location().getPath() + "_" + pos.getX() + "_" + pos.getY() + "_" + pos.getZ();
        }

        return varName;
    }

    private static double parseValueOrVar(JsonElement element, ServerLevel level, BlockPos pos, Entity entity) {
        if (element == null) return 0.0;
        if (element.isJsonPrimitive()) {
            String str = element.getAsString();

            String formatted = formatTextWithVariables(str, level, pos, entity);

            try {
                return Double.parseDouble(formatted);
            } catch (Exception e) {
                try {
                    return evaluateMath(formatted);
                } catch (Exception mathEx) {
                    return 0.0;
                }
            }
        }
        return 0.0;
    }

    public static double evaluateMath(String expression) {
        return new Object() {
            int pos = -1, ch;

            void nextChar() {ch = (++pos < expression.length()) ? expression.charAt(pos) : -1;}

            boolean eat(int charToEat) {
                while (ch == ' ') nextChar();
                if (ch == charToEat) {
                    nextChar();
                    return true;
                }
                return false;
            }

            double parse() {
                nextChar();
                double x = parseExpression();
                if (pos < expression.length()) throw new RuntimeException("Неизвестный символ: " + (char)ch);
                return x;
            }

            double parseExpression() {
                double x = parseTerm();
                for (;;) {
                    if      (eat('+')) x += parseTerm(); // сложение
                    else if (eat('-')) x -= parseTerm(); // вычитание
                    else return x;
                }
            }

            double parseTerm() {
                double x = parseFactor();
                for (;;) {
                    if      (eat('*')) x *= parseFactor(); // умножение
                    else if (eat('/')) x /= parseFactor(); // деление
                    else if (eat('%')) x %= parseFactor(); // остаток от деления
                    else return x;
                }
            }

            double parseFactor() {
                if (eat('+')) return +parseFactor();
                if (eat('-')) return -parseFactor();

                double x;
                int startPos = this.pos;
                if (eat('(')) {
                    x = parseExpression();
                    eat(')');
                } else if ((ch >= '0' && ch <= '9') || ch == '.') {
                    while ((ch >= '0' && ch <= '9') || ch == '.') nextChar();
                    x = Double.parseDouble(expression.substring(startPos, this.pos));
                } else {
                    throw new RuntimeException("Неверный синтаксис: " + (char)ch);
                }

                if (eat('^')) x = Math.pow(x, parseFactor()); // возведение в степень

                return x;
            }
        }.parse();
    }

    private static double parseDouble(Object obj) {
        if (obj instanceof Number num) return num.doubleValue();
        if (obj != null) {
            try {
                return Double.parseDouble(obj.toString());
            } catch (NumberFormatException ignored) {}
        }
        return 0.0;
    }
}