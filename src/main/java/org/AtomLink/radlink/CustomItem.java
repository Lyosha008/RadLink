package org.AtomLink.radlink;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import java.util.Map;
import java.util.List;

public class CustomItem extends Item {
    private final String id;
    private final String trigger;
    private final JsonObject ifCondition;
    private final JsonElement thenActions;
    private final JsonArray lore;

    public CustomItem(String id, String name, int maxStackSize, String trigger,
                      JsonObject ifCondition, JsonElement thenActions, JsonArray lore) {
        super(new Item.Properties().stacksTo(maxStackSize));
        this.id = id;
        this.trigger = trigger;
        this.ifCondition = ifCondition;
        this.thenActions = thenActions;
        this.lore = lore;
    }

    @Override
    public Component getName(ItemStack stack) {return Component.translatable("item." + Radlink.MOD_ID + "." + id);}

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        LoreParser.parseLore(this.lore, tooltip);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemStack = player.getItemInHand(hand);

        if (level.isClientSide) {
            return InteractionResultHolder.pass(itemStack);
        }

        if (trigger == null || !"right_click".equalsIgnoreCase(trigger.trim())) {
            return InteractionResultHolder.pass(itemStack);
        }

        if (!matchesCondition(player, ifCondition)) {
            return InteractionResultHolder.pass(itemStack);
        }

        executeThenActions(player, thenActions);
        return InteractionResultHolder.sidedSuccess(itemStack, level.isClientSide());
    }

    private static boolean matchesCondition(Player player, JsonObject condition) {
        if (condition == null || condition.isJsonNull()) {
            return true;
        }

        JsonObject playerCondition = condition.getAsJsonObject("player");
        if (playerCondition == null || playerCondition.isJsonNull()) {
            return false;
        }

        JsonElement helmetSlot = playerCondition.get("helmet_slot");
        if (helmetSlot == null || !helmetSlot.isJsonObject()) {
            return false;
        }

        JsonObject helmetRule = helmetSlot.getAsJsonObject();
        JsonElement equals = helmetRule.get("==");
        if (equals == null || equals.isJsonNull()) {
            return false;
        }

        ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
        String actualId = BuiltInRegistries.ITEM.getKey(helmet.getItem()).toString();
        return equals.getAsString().equalsIgnoreCase(actualId)
                || equals.getAsString().equalsIgnoreCase("radlink:" + actualId.replace("radlink:", ""));
    }

    private static void executeThenActions(Player player, JsonElement actions) {
        if (actions == null || actions.isJsonNull()) {
            return;
        }

        JsonArray array;
        if (actions.isJsonArray()) {
            array = actions.getAsJsonArray();
        } else if (actions.isJsonObject()) {
            array = new JsonArray();
            array.add(actions.getAsJsonObject());
        } else {
            return;
        }

        if (array.isEmpty()) {
            return;
        }

        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }

            JsonObject action = element.getAsJsonObject();
            if (action.has("say")) {
                JsonObject say = action.getAsJsonObject("say");
                String executor = say.has("executor") ? say.get("executor").getAsString() : "player";
                if (!"player".equalsIgnoreCase(executor)) {
                    continue;
                }

                String who = say.has("who") ? say.get("who").getAsString() : "player";
                String text = say.has("text") ? resolveVariables(say.get("text").getAsString()) : "";

                if ("all".equalsIgnoreCase(who)) {
                    for (Player target : player.level().players()) {
                        target.displayClientMessage(Component.literal(text), false);
                    }
                } else {
                    player.displayClientMessage(Component.literal(text), false);
                }
            }
        }
    }

    private static String resolveVariables(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        String resolved = text;
        for (Map.Entry<String, Object> entry : Radlink.loadWorldVariables().entrySet()) {
            String key = entry.getKey();
            String value = String.valueOf(entry.getValue());
            resolved = resolved.replace("${" + key + "}", value)
                    .replace("{" + key + "}", value);
        }
        return resolved;
    }
}
