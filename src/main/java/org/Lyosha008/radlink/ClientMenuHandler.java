package org.Lyosha008.radlink;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import java.io.InputStream;
import java.io.Reader;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = Radlink.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientMenuHandler {
    private static final List<ResourceLocation> animationFrames = new ArrayList<>();
    private static JsonObject buttonsConfig = null;
    private static JsonArray customButtonsJson = null;
    private static int currentFrameIndex = 0;
    private static long lastFrameTime = 0;
    private static int frameDelayMs = 100;
    private static Path activeWorldDir = null;

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof TitleScreen screen) {
            try {
                if (!RadlinkConfig.IS_MENU_CHANGE.get()) {
                    return;
                }
            } catch (Exception ignored) {
                return;
            }

            try {
                Field splashField = ObfuscationReflectionHelper.findField(TitleScreen.class, "splash");
                splashField.setAccessible(true);
                splashField.set(screen, null);
            } catch (Exception ignored) {}

            loadBackgroundFromWorldConfig();

            if (buttonsConfig != null) {
                for (var child : new ArrayList<>(screen.children())) {
                    if (child instanceof AbstractWidget widget) {
                        String buttonKey = getButtonIdentifier(widget);

                        if (buttonKey != null && buttonsConfig.has(buttonKey)) {
                            JsonElement btnElement = buttonsConfig.get(buttonKey);
                            if (shouldHideButton(btnElement)) {
                                widget.visible = false;
                                widget.active = false;
                                continue;
                            }

                            if (btnElement.isJsonObject()) {
                                JsonObject cfg = btnElement.getAsJsonObject();

                                int x = cfg.has("x") ? cfg.get("x").getAsInt() : widget.getX();
                                int y = cfg.has("y") ? cfg.get("y").getAsInt() : widget.getY();
                                int w = cfg.has("width") ? cfg.get("width").getAsInt() : widget.getWidth();
                                int h = cfg.has("height") ? cfg.get("height").getAsInt() : widget.getHeight();

                                widget.visible = false;
                                widget.active = false;

                                if (cfg.has("texture")) {
                                    String texPath = cfg.get("texture").getAsString();
                                    ResourceLocation texNormal = registerOrGetTexture(activeWorldDir, texPath, buttonKey);

                                    if (texNormal == null) {
                                        texNormal = new ResourceLocation(texPath.contains(":") ? texPath : "radlink:" + texPath);
                                    }

                                    ResourceLocation texHoverFinal = texNormal;
                                    if (cfg.has("textureHover") || cfg.has("hover_texture")) {
                                        String hoverPath = cfg.has("textureHover") ? cfg.get("textureHover").getAsString() : cfg.get("hover_texture").getAsString();
                                        ResourceLocation loadedHover = registerOrGetTexture(activeWorldDir, hoverPath, buttonKey + "_hover");
                                        if (loadedHover != null) {
                                            texHoverFinal = loadedHover;
                                        } else {
                                            texHoverFinal = new ResourceLocation(hoverPath.contains(":") ? hoverPath : "radlink:" + hoverPath);
                                        }
                                    }

                                    ResourceLocation finalTexNormal = texNormal;
                                    ResourceLocation finalTexHover = texHoverFinal;

                                    int texSrcW = getJsonInt(cfg, "textureWidth", "texture_width", 480);
                                    int texSrcH = getJsonInt(cfg, "textureHeight", "texture_height", 50);

                                    event.addListener(new AbstractWidget(x, y, w, h, widget.getMessage()) {
                                        @Override
                                        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
                                            RenderSystem.enableBlend();
                                            RenderSystem.defaultBlendFunc();

                                            ResourceLocation textureToDraw = this.isHoveredOrFocused() ? finalTexHover : finalTexNormal;

                                            graphics.blit(
                                                textureToDraw,
                                                this.getX(), this.getY(),
                                                this.width, this.height,
                                                0.0F, 0.0F,
                                                texSrcW, texSrcH,
                                                texSrcW, texSrcH
                                            );
                                        }

                                        @Override
                                        public void onClick(double mouseX, double mouseY) {
                                            if (widget instanceof Button originalBtn) {
                                                originalBtn.onPress();
                                            }
                                        }

                                        @Override
                                        protected void updateWidgetNarration(NarrationElementOutput output) {}
                                    });
                                } else {
                                    event.addListener(new AbstractWidget(x, y, w, h, widget.getMessage()) {
                                        @Override
                                        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
                                            int color = this.isHoveredOrFocused() ? 0xFFFF16 : 0xFFFFFF;

                                            graphics.drawCenteredString(
                                                Minecraft.getInstance().font,
                                                this.getMessage(),
                                                this.getX() + this.width / 2,
                                                this.getY() + (this.height - 8) / 2,
                                                color
                                            );
                                        }

                                        @Override
                                        public void onClick(double mouseX, double mouseY) {
                                            if (widget instanceof Button originalBtn) {
                                                originalBtn.onPress();
                                            }
                                        }

                                        @Override
                                        protected void updateWidgetNarration(NarrationElementOutput output) {}
                                    });
                                }
                            }
                            else if ("realms".equals(buttonKey) || "accessibility".equals(buttonKey) || "language".equals(buttonKey)) {
                                widget.visible = false;
                                widget.active = false;
                            }
                        }
                    }
                }
            }

            if (customButtonsJson != null) {
                int customIndex = 0;
                for (JsonElement element : customButtonsJson) {
                    if (!element.isJsonObject()) continue;
                    JsonObject btnObj = element.getAsJsonObject();

                    if (shouldHideButton(btnObj)) {
                        continue;
                    }

                    String text = btnObj.has("text") ? btnObj.get("text").getAsString() : "Custom Button";
                    int x = btnObj.has("x") ? btnObj.get("x").getAsInt() : 0;
                    int y = btnObj.has("y") ? btnObj.get("y").getAsInt() : 0;
                    int w = btnObj.has("width") ? btnObj.get("width").getAsInt() : 150;
                    int h = btnObj.has("height") ? btnObj.get("height").getAsInt() : 20;
                    String url = btnObj.has("url") ? btnObj.get("url").getAsString() : null;

                    if (btnObj.has("texture")) {
                        String texPath = btnObj.get("texture").getAsString();
                        ResourceLocation texNormal = registerOrGetTexture(activeWorldDir, texPath, "custom_" + customIndex);

                        if (texNormal == null) {
                            texNormal = new ResourceLocation(texPath.contains(":") ? texPath : "radlink:" + texPath);
                        }

                        ResourceLocation texHoverFinal = texNormal;
                        if (btnObj.has("textureHover") || btnObj.has("hover_texture")) {
                            String hoverPath = btnObj.has("textureHover") ? btnObj.get("textureHover").getAsString() : btnObj.get("hover_texture").getAsString();
                            ResourceLocation loadedHover = registerOrGetTexture(activeWorldDir, hoverPath, "custom_" + customIndex + "_hover");
                            if (loadedHover != null) {
                                texHoverFinal = loadedHover;
                            } else {
                                texHoverFinal = new ResourceLocation(hoverPath.contains(":") ? hoverPath : "radlink:" + hoverPath);
                            }
                        }

                        ResourceLocation finalTexNormal = texNormal;
                        ResourceLocation finalTexHover = texHoverFinal;
                        int texSrcW = getJsonInt(btnObj, "textureWidth", "texture_width", 480);
                        int texSrcH = getJsonInt(btnObj, "textureHeight", "texture_height", 50);

                        customIndex++;

                        event.addListener(new AbstractWidget(x, y, w, h, Component.literal(text)) {
                            @Override
                            public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
                                RenderSystem.enableBlend();
                                RenderSystem.defaultBlendFunc();

                                ResourceLocation textureToDraw = this.isHoveredOrFocused() ? finalTexHover : finalTexNormal;

                                graphics.blit(
                                    textureToDraw,
                                    this.getX(), this.getY(),
                                    this.width, this.height,
                                    0.0F, 0.0F,
                                    texSrcW, texSrcH,
                                    texSrcW, texSrcH
                                );
                            }

                            @Override
                            public void onClick(double mouseX, double mouseY) {
                                if (url != null && !url.isEmpty()) {
                                    try {
                                        Util.getPlatform().openUri(new URI(url));
                                    } catch (Exception e) {
                                        System.err.println("[RadLink] Ошибка открытия ссылки: " + url);
                                    }
                                }
                            }

                            @Override
                            protected void updateWidgetNarration(NarrationElementOutput output) {}
                        });
                    } else {
                        customIndex++;
                        event.addListener(new AbstractWidget(x, y, w, h, Component.literal(text)) {
                            @Override
                            public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
                                int color = this.isHoveredOrFocused() ? 0xFFFF16 : 0xFFFFFF;

                                graphics.drawCenteredString(
                                    Minecraft.getInstance().font,
                                    this.getMessage(),
                                    this.getX() + this.width / 2,
                                    this.getY() + (this.height - 8) / 2,
                                    color
                                );
                            }

                            @Override
                            public void onClick(double mouseX, double mouseY) {
                                if (url != null && !url.isEmpty()) {
                                    try {
                                        Util.getPlatform().openUri(new URI(url));
                                    } catch (Exception e) {
                                        System.err.println("[RadLink] Ошибка открытия ссылки: " + url);
                                    }
                                }
                            }

                            @Override
                            protected void updateWidgetNarration(NarrationElementOutput output) {}
                        });
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onScreenRenderPre(ScreenEvent.Render.Pre event) {
        if (event.getScreen() instanceof TitleScreen screen) {
            try {
                if (!RadlinkConfig.IS_MENU_CHANGE.get()) return;
            } catch (Exception ignored) {
                return;
            }

            if (animationFrames.isEmpty()) return;

            GuiGraphics graphics = event.getGuiGraphics();
            int mouseX = event.getMouseX();
            int mouseY = event.getMouseY();
            float partialTick = event.getPartialTick();

            long currentTime = System.currentTimeMillis();
            if (currentTime - lastFrameTime >= frameDelayMs) {
                currentFrameIndex = (currentFrameIndex + 1) % animationFrames.size();
                lastFrameTime = currentTime;
            }

            ResourceLocation currentTexture = animationFrames.get(currentFrameIndex);

            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

            graphics.blit(
                currentTexture,
                0, 0,
                0.0F, 0.0F,
                screen.width, screen.height,
                screen.width, screen.height
            );

            for (GuiEventListener child : screen.children()) {
                if (child instanceof AbstractWidget widget && widget.visible) {
                    widget.render(graphics, mouseX, mouseY, partialTick);
                }
            }

            event.setCanceled(true);
        }
    }

    private static boolean shouldHideButton(JsonElement element) {
        if (element == null) return false;

        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            if (obj.has("use")) {
                JsonElement useElem = obj.get("use");
                if (useElem.isJsonPrimitive()) {
                    String useVal = useElem.getAsString().trim().toLowerCase();
                    return useVal.equals("no") || useVal.equals("false") || useVal.equals("0");
                }
            }
        }
        return false;
    }

    private static int getJsonInt(JsonObject obj, String key1, String key2, int defaultValue) {
        if (obj.has(key1)) return obj.get(key1).getAsInt();
        if (obj.has(key2)) return obj.get(key2).getAsInt();
        return defaultValue;
    }

    private static void parseButtonsConfig(JsonObject rootJson) {
        if (rootJson.has("buttons") && rootJson.get("buttons").isJsonObject()) {
            buttonsConfig = rootJson.getAsJsonObject("buttons");
        }

        if (rootJson.has("custom_buttons") && rootJson.get("custom_buttons").isJsonArray()) {
            customButtonsJson = rootJson.getAsJsonArray("custom_buttons");
        } else {
            customButtonsJson = null;
        }
    }

    private static String getButtonIdentifier(AbstractWidget widget) {
        Component message = widget.getMessage();

        if (message != null && message.getContents() instanceof TranslatableContents trans) {
            String key = trans.getKey();
            switch (key) {
                case "menu.singleplayer": return "singleplayer";
                case "menu.multiplayer": return "multiplayer";
                case "fml.menu.mods":
                case "modmenu.title": return "mods";
                case "menu.options": return "options";
                case "menu.quit": return "quit";
                case "menu.online":
                case "mcl.realms": return "realms";
                case "options.accessibility":
                case "title.accessibility": return "accessibility";
                case "options.language":
                case "narration.language": return "language";
            }
        }

        if (message != null) {
            String rawText = message.getString().toLowerCase();
            if (rawText.contains("singleplayer") || rawText.contains("одиночная")) return "singleplayer";
            if (rawText.contains("multiplayer") || rawText.contains("сетевая")) return "multiplayer";
            if (rawText.contains("mods") || rawText.contains("моды")) return "mods";
            if (rawText.contains("options") || rawText.contains("настройки")) return "options";
            if (rawText.contains("quit") || rawText.contains("выйти")) return "quit";
            if (rawText.contains("language") || rawText.contains("язык")) return "language";
            if (rawText.contains("accessibility") || rawText.contains("доступность")) return "accessibility";
        }

        if (widget instanceof ImageButton imageButton) {
            String widgetName = imageButton.toString().toLowerCase();
            if (widgetName.contains("language") || widgetName.contains("lang")) {
                return "language";
            }
        }

        return null;
    }

    private static void loadBackgroundFromWorldConfig() {
        animationFrames.clear();
        activeWorldDir = null;

        Path gameDir = Minecraft.getInstance().gameDirectory.toPath();

        String configTarget = "";
        try {
            configTarget = RadlinkConfig.SERVER_OR_WORLD_NAME.get().trim();
        } catch (Exception ignored) {}

        if (!configTarget.isEmpty()) {
            String safeTargetName = configTarget.replaceAll("[^a-zA-Z0-9_\\-]", "_");

            Path worldPath = gameDir.resolve("saves").resolve(configTarget).resolve("radlink");
            if (!Files.exists(worldPath)) {
                worldPath = gameDir.resolve("saves").resolve(safeTargetName).resolve("radlink");
            }

            if (!Files.exists(worldPath)) {
                worldPath = gameDir.resolve("radlink").resolve("servers").resolve(safeTargetName);
            }

            if (Files.exists(worldPath)) {
                activeWorldDir = worldPath;
            } else {
                System.err.println("[RadLink] Папка для '" + configTarget + "' не найдена (" + worldPath.toAbsolutePath() + ")");
            }
        }

        if (activeWorldDir == null) {
            activeWorldDir = Radlink.resolveWorldDataDir();

            if (activeWorldDir == null) {
                Path savesDir = gameDir.resolve("saves");
                if (Files.exists(savesDir)) {
                    try (var stream = Files.list(savesDir)) {
                        activeWorldDir = stream.filter(Files::isDirectory)
                            .max((p1, p2) -> Long.compare(p1.toFile().lastModified(), p2.toFile().lastModified()))
                            .map(p -> p.resolve("radlink"))
                            .orElse(null);
                    } catch (Exception e) {
                        System.err.println("[RadLink] Ошибка поиска сохранения: " + e.getMessage());
                    }
                }
            }
        }

        if (activeWorldDir == null || !Files.exists(activeWorldDir)) {
            System.err.println("[RadLink] Папка конфигурации фона не найдена!");
            return;
        }

        Path menuJsonFile = activeWorldDir.resolve("menu_background.json");
        if (Files.exists(menuJsonFile)) {
            try (Reader reader = Files.newBufferedReader(menuJsonFile, StandardCharsets.UTF_8)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();

                parseButtonsConfig(json);

                if (json.has("fps")) {
                    int fps = Math.max(1, json.get("fps").getAsInt());
                    frameDelayMs = 1000 / fps;
                }

                if (json.has("frames") && json.get("frames").isJsonArray()) {
                    JsonArray framesArray = json.getAsJsonArray("frames");
                    int index = 0;
                    for (JsonElement element : framesArray) {
                        ResourceLocation loc = registerOrGetTexture(activeWorldDir, element.getAsString(), "anim_" + index++);
                        if (loc != null) animationFrames.add(loc);
                    }
                } else if (json.has("texture")) {
                    ResourceLocation loc = registerOrGetTexture(activeWorldDir, json.get("texture").getAsString(), "single_bg");
                    if (loc != null) animationFrames.add(loc);
                }
            } catch (Exception e) {
                System.err.println("[RadLink] Ошибка разбора JSON: " + e.getMessage());
            }
        } else {
            System.err.println("[RadLink] Файл menu_background.json не найден по пути: " + menuJsonFile.toAbsolutePath());
        }
    }

    private static ResourceLocation registerOrGetTexture(Path worldDir, String textureStr, String uniqueKey) {
        if (textureStr == null || textureStr.isEmpty()) return null;

        if (textureStr.contains(":") && !textureStr.startsWith("assets/")) {
            return new ResourceLocation(textureStr);
        }

        if (worldDir != null) {
            String filename = textureStr;
            if (filename.contains("/")) {
                filename = filename.substring(filename.lastIndexOf('/') + 1);
            }

            Path imagePath = worldDir.resolve(filename);
            if (!Files.exists(imagePath)) {
                imagePath = worldDir.resolve(textureStr);
            }

            if (Files.exists(imagePath)) {
                try (InputStream is = Files.newInputStream(imagePath)) {
                    NativeImage nativeImage = NativeImage.read(is);
                    DynamicTexture dynamicTexture = new DynamicTexture(nativeImage);
                    System.out.println("[RadLink] Загружена динамическая текстура: " + imagePath.toAbsolutePath());
                    return Minecraft.getInstance().getTextureManager().register("radlink_dynamic_" + uniqueKey, dynamicTexture);
                } catch (Exception e) {
                    System.err.println("[RadLink] Ошибка чтения PNG: " + e.getMessage());
                }
            } else {
                System.err.println("[RadLink] Файл текстуры не найден: " + imagePath.toAbsolutePath());
            }
        }
        return null;
    }
}