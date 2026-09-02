package org.RadLink.radlink;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class CustomDefinitions {
    private static final Gson GSON = new Gson();
    private static final Type ITEM_TYPE = new TypeToken<List<CustomItemDefinition>>() {}.getType();
    private static final Type BLOCK_TYPE = new TypeToken<List<CustomBlockDefinition>>() {}.getType();
    private static final Type ARMOR_TYPE = new TypeToken<List<CustomArmorDefinition>>() {}.getType();

    private CustomDefinitions() {}

    public record CustomScriptDefinition(
        String id,
        String trigger,
        @SerializedName("if") JsonObject ifCondition,
        @SerializedName("then") JsonElement thenActions
    ) {
        public boolean isSuper() {
            if (ifCondition == null) return false;
            if (ifCondition.has("super") && ifCondition.get("super").getAsBoolean()) {
                return true;
            }
            return false;
        }
    }

    public static List<CustomScriptDefinition> loadScripts(Path worldRoot) {
        Type type = new TypeToken<List<CustomScriptDefinition>>() {}.getType();
        return readList(resolveWorldFile(worldRoot, "global.json"), type);
    }

    public static List<CustomItemDefinition> loadItems(Path worldRoot) {
        return readList(resolveWorldFile(worldRoot, "items.json"), ITEM_TYPE);
    }

    public static List<CustomBlockDefinition> loadBlocks(Path worldRoot) {
        return readList(resolveWorldFile(worldRoot, "blocks.json"), BLOCK_TYPE);
    }

    public static List<CustomArmorDefinition> loadArmors(Path worldRoot) {
        return readList(resolveWorldFile(worldRoot, "armor_item.json"), ARMOR_TYPE);
    }

    public static List<CustomEffectDefinition> loadEffects(Path worldRoot) {
        Type type = new TypeToken<List<CustomEffectDefinition>>() {}.getType();
        return readList(resolveWorldFile(worldRoot, "effects.json"), type);
    }

    private static Path resolveWorldFile(Path worldRoot, String fileName) {
        if (worldRoot == null) {
            return null;
        }
        return worldRoot.resolve(fileName);
    }

    private static <T> List<T> readList(Path file, Type type) {
        if (file == null || Files.notExists(file)) {
            return new ArrayList<>();
        }

        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            List<T> values = GSON.fromJson(reader, type);
            return values == null ? new ArrayList<>() : values;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load custom definitions from " + file, e);
        }
    }

    public record CustomItemDefinition(
        String id,
        String name,
        int maxStackSize,
        String trigger,
        @SerializedName("if") JsonObject ifCondition,
        @SerializedName("then") JsonElement thenActions,
        String texture,
        String slot,
        String material,
        JsonArray lore) {
    }

    public record CustomBlockDefinition(
        String id,
        String name,
        int maxStackSize,
        float hardness,
        float resistance,
        boolean directional) {
    }

    public record CustomArmorDefinition(
        String id,
        String name,
        int maxStackSize,
        String slot,
        String material,
        String texture,
        int durability,
        JsonArray lore) {
    }

    public record CustomEffectDefinition(
        String id,
        String name,
        String color,
        boolean isBad
    ) {}
}