package org.Lyosha008.radlink;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.items.ItemStackHandler;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class CustomSlotManager {
    public static class SlotConfig {
        public String id;
        public int index;
        public int x;
        public int y;
        public ResourceLocation icon;
        public int maxStack = 64;
        public String allowedItem = "";
        public com.google.gson.JsonObject ifCondition;

        public SlotConfig() {}

        public SlotConfig(String id, int index) {
            this.id = id;
            this.index = index;
        }
    }

    public static void loadSlotFromJson(ResourceLocation location, InputStreamReader reader) {
        JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
        SlotConfig config = new SlotConfig();
        config.id = json.get("slot_id").getAsString();
        config.x = json.get("x").getAsInt();
        config.y = json.get("y").getAsInt();

        if (json.has("if")) {
            config.ifCondition = json.getAsJsonObject("if");
        }

        REGISTERED_SLOTS.put(config.id, config);
    }

    public static final Map<String, SlotConfig> REGISTERED_SLOTS = new HashMap<>();
    private static final Map<Player, ItemStackHandler> PLAYER_SLOTS = new HashMap<>();

    public static ItemStackHandler getPlayerHandler(Player player) {
        return player.getCapability(PlayerCustomSlots.CAPABILITY).orElseGet(() -> new ItemStackHandler(5));
    }
}