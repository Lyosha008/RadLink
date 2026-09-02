package org.AtomLink.radlink;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;
import java.util.Map;

public class LoreParser {

    public static void parseLore(JsonArray loreArray, List<Component> tooltip) {
        if (loreArray == null || loreArray.isEmpty()) return;

        for (JsonElement element : loreArray) {
            if (element == null || element.isJsonNull()) continue;

            if (element.isJsonPrimitive()) {
                tooltip.add(Component.literal(element.getAsString()));
                continue;
            }

            if (element.isJsonObject()) {
                JsonObject obj = element.getAsJsonObject();
                if (obj.has("text")) {
                    JsonElement textElem = obj.get("text");

                    if (textElem.isJsonArray()) {
                        MutableComponent lineComponent = Component.empty();
                        boolean lineItalic = false;

                        for (JsonElement part : textElem.getAsJsonArray()) {
                            if (part.isJsonObject() && part.getAsJsonObject().has("italic")) {
                                lineItalic = Boolean.parseBoolean(part.getAsJsonObject().get("italic").getAsString());
                            }
                        }

                        for (JsonElement part : textElem.getAsJsonArray()) {
                            if (!part.isJsonObject()) continue;
                            JsonObject partObj = part.getAsJsonObject();

                            for (Map.Entry<String, JsonElement> entry : partObj.entrySet()) {
                                String key = entry.getKey();
                                String val = entry.getValue().getAsString();

                                if ("italic".equalsIgnoreCase(key)) continue;

                                if ("\n".equals(val)) {
                                    tooltip.add(lineComponent);
                                    lineComponent = Component.empty();
                                    continue;
                                }

                                ChatFormatting color = parseColor(key);
                                MutableComponent partComponent = Component.literal(val);

                                final boolean finalItalic = lineItalic;
                                final ChatFormatting finalColor = color;

                                partComponent.withStyle(style -> style.withColor(finalColor).withItalic(finalItalic));
                                lineComponent.append(partComponent);
                            }
                        }

                        if (!lineComponent.getString().isEmpty()) {
                            tooltip.add(lineComponent);
                        }
                    } else if (textElem.isJsonPrimitive()) {
                        tooltip.add(Component.literal(textElem.getAsString()));
                    }
                }
            }
        }
    }

    private static ChatFormatting parseColor(String colorName) {
        try {
            return ChatFormatting.valueOf(colorName.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ChatFormatting.WHITE;
        }
    }
}