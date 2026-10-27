package org.Lyosha008.radlink;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import java.util.List;

public class CustomItem extends Item {
    private final String id;
    private final boolean food;
    private final String trigger;
    private final JsonObject ifCondition;
    private final JsonArray lore;

    public CustomItem(
        String id,
        Item.Properties properties,
        boolean food,
        String trigger,
        JsonObject ifCondition,
        JsonArray lore
    ) {
        super(properties);
        this.id = id;
        this.food = food;
        this.trigger = trigger;
        this.ifCondition = ifCondition;
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
        if (food || trigger == null || !"right_click".equalsIgnoreCase(trigger.trim())) {
            return super.use(level, player, hand);
        }

        if (level.isClientSide || !(level instanceof ServerLevel serverLevel)
            || !matchesTriggerCondition(player)) {
            return InteractionResultHolder.pass(itemStack);
        }

        if (ifCondition != null
            && !ScriptEngine.checkConditions(ifCondition, null, serverLevel, player.blockPosition(), player)) {
            return InteractionResultHolder.pass(itemStack);
        }

        return InteractionResultHolder.sidedSuccess(itemStack, level.isClientSide());
    }

    public boolean matchesTriggerCondition(Player player) {
        return ifCondition == null || !ifCondition.has("player") || matchesCondition(player, ifCondition);
    }

    private static boolean matchesCondition(Player player, JsonObject condition) {
        if (condition == null || condition.isJsonNull()) {
            return true;
        }

        JsonElement playerConditionElement = condition.get("player");
        if (playerConditionElement == null || !playerConditionElement.isJsonObject()) {
            return false;
        }
        JsonObject playerCondition = playerConditionElement.getAsJsonObject();

        JsonElement helmetSlot = playerCondition.get("helmet_slot");
        if (helmetSlot == null || !helmetSlot.isJsonObject()) {
            return false;
        }

        JsonElement chestSlot = playerCondition.get("chest_slot");
        if (chestSlot == null || !chestSlot.isJsonObject()) {
            return false;
        }

        return matchesEquipmentRule(player.getItemBySlot(EquipmentSlot.HEAD), helmetSlot)
                && matchesEquipmentRule(player.getItemBySlot(EquipmentSlot.CHEST), chestSlot);
    }

    private static boolean matchesEquipmentRule(ItemStack stack, JsonElement ruleElement) {
        JsonElement equals = ruleElement.getAsJsonObject().get("==");
        if (equals == null || equals.isJsonNull()) {
            return false;
        }

        String actualId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return equals.getAsString().equalsIgnoreCase(actualId);
    }
}
