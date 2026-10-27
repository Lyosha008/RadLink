package org.Lyosha008.radlink;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.resource.PathPackResources;
import net.minecraftforge.server.ServerLifecycleHooks;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

@Mod(Radlink.MOD_ID)
public class Radlink {
    public static final String MOD_ID = "radlink";

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);
    public static final DeferredRegister<MobEffect> MOB_EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, MOD_ID);

    private static final List<RegistryObject<Item>> CUSTOM_ITEMS = new ArrayList<>();
    private static final List<RegistryObject<Block>> CUSTOM_BLOCKS = new ArrayList<>();

    public static final RegistryObject<CreativeModeTab> RADLINK_TAB = CREATIVE_MODE_TABS.register("radlink_tab",
        () -> CreativeModeTab.builder()
            .icon(() -> {
                if (!CUSTOM_ITEMS.isEmpty()) {
                    return new ItemStack(CUSTOM_ITEMS.get(0).get());
                }
                return new ItemStack(Items.DIAMOND);
            })
            .title(Component.literal("Radlink"))
            .displayItems((parameters, output) -> {
                if (!RadlinkConfig.IS_CREATIVE_TAB.get()) {
                    return;
                }

                for (RegistryObject<Item> itemReg : ITEMS.getEntries()) {
                    output.accept(itemReg.get());
                }
            })
            .build());

    public Radlink() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        RadlinkConfig.register();
        StartupContentPack.loadBeforeRegistries();
        RadlinkNetwork.register();
        registerCustomContent();

        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        MOB_EFFECTS.register(modEventBus);

        MinecraftForge.EVENT_BUS.register(new WorldTickHandler());

        MinecraftForge.EVENT_BUS.addListener((BlockEvent.BreakEvent event) -> {
            if (!event.getLevel().isClientSide() && event.getLevel() instanceof ServerLevel level) {
                ScriptEngine.clearBlockVariables(level, event.getPos());
            }
        });
    }

    private void registerCustomContent() {
        Path startupContentDir = StartupContentPack.getDownloadedDirectory();
        Path worldDataDir = startupContentDir != null
            ? startupContentDir
            : StartupContentPack.isRemoteTargetSelected() ? null : resolveWorldDataDir();
        ensureWorldAssets(worldDataDir);

        for (CustomDefinitions.CustomItemDefinition itemDefinition : CustomDefinitions.loadItems(worldDataDir)) {
            String itemId = sanitizeId(itemDefinition.id());
            CUSTOM_ITEMS.add(ITEMS.register(itemId, () -> createCustomItem(itemId, itemDefinition)));
        }

        for (CustomDefinitions.CustomArmorDefinition armorDef : CustomDefinitions.loadArmors(worldDataDir)) {
            String armorId = sanitizeId(armorDef.id());
            if (isHorseArmorSlot(armorDef.slot())) {
                ITEMS.register(armorId, () -> new CustomHorseArmorItem(armorId, armorDef.protection(),
                    armorDef.maxStackSize(), armorDef.horseTexture(), armorDef.lore()));
            } else {
                ArmorItem.Type type = parseArmorType(armorDef.slot());
                ArmorMaterial material = createArmorMaterial(armorDef.material(), armorDef.durability(), armorDef.defense());
                String armorTexture = armorDef.texture();
                ITEMS.register(armorId, () -> new CustomArmorItem(armorId, material, type,
                    armorDef.maxStackSize(), armorTexture, armorDef.lore()));
            }
        }

        for (CustomDefinitions.CustomBlockDefinition blockDefinition : CustomDefinitions.loadBlocks(worldDataDir)) {
            String blockId = sanitizeId(blockDefinition.id());

            BlockBehaviour.Properties props = BlockBehaviour.Properties.of()
                .strength(blockDefinition.hardness(), blockDefinition.resistance());

            RegistryObject<Block> block = BLOCKS.register(blockId, () ->
                blockDefinition.directional()
                    ? new CustomDirectionalBlock(props)
                    : new CustomBlock(blockDefinition.name(), blockDefinition.hardness(), blockDefinition.resistance())
            );

            CUSTOM_BLOCKS.add(block);
            ITEMS.register(blockId,
                () -> new CustomBlockItem(block.get(), blockId, blockDefinition.name(), blockDefinition.maxStackSize()));
        }

        for (CustomDefinitions.CustomEffectDefinition effectDef : CustomDefinitions.loadEffects(worldDataDir)) {
            String effectId = sanitizeId(effectDef.id());

            int parsedColor = 0x808080;
            if (effectDef.color() != null && !effectDef.color().isBlank()) {
                try {
                    String hex = effectDef.color().replace("#", "").replace("0x", "");
                    parsedColor = Integer.parseInt(hex, 16);
                } catch (NumberFormatException ignored) {}
            }

            final int finalColor = parsedColor;
            MobEffectCategory category = effectDef.isBad() ? MobEffectCategory.HARMFUL : MobEffectCategory.BENEFICIAL;

            MOB_EFFECTS.register(effectId, () -> new CustomMobEffect(category, finalColor));
        }
    }

    public static void saveWorldVariables(Map<String, Object> vars) {
        Path worldRoot = resolveWorldDataDir();
        if (worldRoot == null) return;

        Path variablesFile = worldRoot.resolve("variables.json");
        try (Writer writer = Files.newBufferedWriter(variablesFile, StandardCharsets.UTF_8)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(vars, writer);
        } catch (IOException e) {}
    }

    public static Map<String, Object> loadWorldVariables() {
        Path worldRoot = resolveWorldDataDir();
        if (worldRoot == null) {
            return new LinkedHashMap<>();
        }

        Path variablesFile = worldRoot.resolve("variables.json");
        if (!Files.exists(variablesFile)) {
            return new LinkedHashMap<>();
        }

        try (Reader reader = Files.newBufferedReader(variablesFile, StandardCharsets.UTF_8)) {
            Object value = new Gson().fromJson(reader, Object.class);
            if (value instanceof Map<?, ?> map) {
                Map<String, Object> result = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    result.put(String.valueOf(entry.getKey()), entry.getValue());
                }
                return result;
            }
        } catch (Exception e) {
            System.err.println("[Radlink] Ошибка чтения variables.json: " + e.getMessage());
        }

        return new LinkedHashMap<>();
    }

    public static CustomDefinitions.CustomScriptDefinition getScriptForElement(String elementId) {
        if (elementId == null) return null;

        CustomDefinitions.CustomScriptDefinition globalScript = getGlobalScriptForElement(elementId);
        if (globalScript != null) {
            return globalScript;
        }

        return getItemScriptForElement(elementId);
    }

    public static CustomDefinitions.CustomScriptDefinition getGlobalScriptForElement(String elementId) {
        if (elementId == null) return null;

        Path worldDataDir = resolveWorldDataDir();
        if (worldDataDir == null) return null;

        String cleanId = elementId.contains(":") ? elementId.substring(elementId.indexOf(":") + 1) : elementId;
        for (CustomDefinitions.CustomScriptDefinition script : CustomDefinitions.loadScripts(worldDataDir)) {
            if (script.id() != null && (cleanId.equalsIgnoreCase(script.id()) || elementId.equalsIgnoreCase(script.id()))) {
                return script;
            }
        }

        return null;
    }

    public static CustomDefinitions.CustomScriptDefinition getItemScriptForElement(String elementId) {
        if (elementId == null) return null;

        Path worldDataDir = resolveWorldDataDir();
        if (worldDataDir == null) return null;

        String cleanId = elementId.contains(":") ? elementId.substring(elementId.indexOf(":") + 1) : elementId;
        for (CustomDefinitions.CustomItemDefinition item : CustomDefinitions.loadItems(worldDataDir)) {
            if (item.id() != null && (cleanId.equalsIgnoreCase(item.id()) || elementId.equalsIgnoreCase(item.id()))) {
                if (item.trigger() == null) {
                    return null;
                }
                return new CustomDefinitions.CustomScriptDefinition(
                    item.id(),
                    item.trigger(),
                    item.ifCondition(),
                    item.thenActions()
                );
            }
        }

        return null;
    }

    static Path resolveWorldDataDir() {
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && mc.level.getServer() != null) {
                return mc.level.getServer().getWorldPath(LevelResource.ROOT).resolve("radlink");
            }
        }

        MinecraftServer currentServer = ServerLifecycleHooks.getCurrentServer();
        if (currentServer != null) {
            return currentServer.getWorldPath(LevelResource.ROOT).resolve("radlink");
        }

        if (net.minecraftforge.fml.loading.FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT) {
            Path startupContentDir = StartupContentPack.getDownloadedDirectory();
            if (startupContentDir != null) {
                return startupContentDir;
            }
            if (StartupContentPack.isRemoteTargetSelected()) {
                return null;
            }
        } else {
            return resolveDedicatedServerWorldDataDir();
        }

        Path gameDir = FMLPaths.GAMEDIR.get();
        Path savesDir = gameDir.resolve("saves");
        if (Files.isDirectory(savesDir)) {
            try (Stream<Path> worlds = Files.list(savesDir)) {
                return worlds.filter(Files::isDirectory)
                    .map(world -> world.resolve("radlink"))
                    .max(Comparator.comparingLong(path -> {
                        try {
                            return Files.exists(path) ? Files.getLastModifiedTime(path).toMillis() : 0L;
                        } catch (IOException e) {
                            return 0L;
                        }
                    }))
                    .orElseGet(() -> {
                        Path runDataPack = gameDir.resolve("run-data").resolve("radlink");
                        if (Files.isDirectory(runDataPack)) return runDataPack;
                        return gameDir.resolve("saves").resolve("world").resolve("radlink");
                    });
            } catch (IOException e) {
                Path runDataPack = gameDir.resolve("run-data").resolve("radlink");
                if (Files.isDirectory(runDataPack)) return runDataPack;
                return gameDir.resolve("saves").resolve("world").resolve("radlink");
            }
        }

        Path runDataPack = gameDir.resolve("run-data").resolve("radlink");
        if (Files.isDirectory(runDataPack)) return runDataPack;

        return gameDir.resolve("saves").resolve("world").resolve("radlink");
    }

    private static Path resolveDedicatedServerWorldDataDir() {
        Path gameDir = FMLPaths.GAMEDIR.get().toAbsolutePath().normalize();
        Path propertiesFile = gameDir.resolve("server.properties");
        String levelName = "world";

        if (Files.isRegularFile(propertiesFile)) {
            Properties properties = new Properties();
            try (InputStream input = Files.newInputStream(propertiesFile)) {
                properties.load(input);
                levelName = properties.getProperty("level-name", levelName).trim();
            } catch (IOException e) {
                System.err.println("[Radlink] Не удалось прочитать server.properties: " + e.getMessage());
            }
        }

        try {
            Path configuredWorld = Path.of(levelName);
            Path worldPath = configuredWorld.isAbsolute() ? configuredWorld : gameDir.resolve(configuredWorld);
            worldPath = worldPath.toAbsolutePath().normalize();
            if (!configuredWorld.isAbsolute() && !worldPath.startsWith(gameDir)) {
                throw new IllegalArgumentException("level-name выходит за пределы папки сервера");
            }
            return worldPath.resolve("radlink");
        } catch (IllegalArgumentException e) {
            System.err.println("[Radlink] Некорректный level-name в server.properties: " + e.getMessage());
            return gameDir.resolve("world").resolve("radlink");
        }
    }

    private static void ensureWorldAssets(Path worldRoot) {
        if (worldRoot == null) {
            return;
        }

        try {
            Path assetsDir = worldRoot.resolve("assets").resolve(MOD_ID);
            Path itemModelsDir = assetsDir.resolve("models").resolve("item");
            Path blockModelsDir = assetsDir.resolve("models").resolve("block");
            Path texturesItemDir = assetsDir.resolve("textures").resolve("item");
            Path texturesBlockDir = assetsDir.resolve("textures").resolve("block");
            Path texturesHorseArmorDir = assetsDir.resolve("textures").resolve("entity").resolve("horse").resolve("armor");
            Path blockStatesDir = assetsDir.resolve("blockstates");
            Path langDir = assetsDir.resolve("lang");

            Files.createDirectories(itemModelsDir);
            Files.createDirectories(blockModelsDir);
            Files.createDirectories(texturesItemDir);
            Files.createDirectories(texturesBlockDir);
            Files.createDirectories(texturesHorseArmorDir);
            Files.createDirectories(blockStatesDir);
            Files.createDirectories(langDir);

            if (Files.notExists(worldRoot.resolve("pack.mcmeta"))) {
                Files.writeString(worldRoot.resolve("pack.mcmeta"),
                    "{\n  \"pack\": {\n    \"pack_format\": 15,\n    \"description\": \"RadLink custom world assets\"\n  }\n}\n",
                    StandardCharsets.UTF_8);
            }

            for (CustomDefinitions.CustomItemDefinition item : CustomDefinitions.loadItems(worldRoot)) {
                String itemId = sanitizeId(item.id());
                Path modelPath = itemModelsDir.resolve(itemId + ".json");
                if (Files.notExists(modelPath)) {
                    String textureRef = item.texture() != null && !item.texture().isBlank()
                        ? item.texture()
                        : (Files.exists(texturesItemDir.resolve(itemId + ".png")) ? "radlink:item/" + itemId : "minecraft:item/diamond");
                    Files.writeString(modelPath,
                        "{\n  \"parent\": \"minecraft:item/generated\",\n  \"textures\": {\n    \"layer0\": \"" + textureRef + "\"\n  }\n}\n",
                        StandardCharsets.UTF_8);
                }
            }

            for (CustomDefinitions.CustomArmorDefinition armor : CustomDefinitions.loadArmors(worldRoot)) {
                String armorId = sanitizeId(armor.id());
                Path modelPath = itemModelsDir.resolve(armorId + ".json");
                if (Files.notExists(modelPath)) {
                    String providedRef = armor.texture();
                    String textureRef = (providedRef != null && !providedRef.isBlank())
                        ? (providedRef.contains(":") ? providedRef : MOD_ID + ":item/" + providedRef)
                        : (Files.exists(texturesItemDir.resolve(armorId + ".png")) ? "radlink:item/" + armorId : "minecraft:item/diamond_helmet");

                    Files.writeString(modelPath,
                        "{\n  \"parent\": \"minecraft:item/generated\",\n  \"textures\": {\n    \"layer0\": \"" + textureRef + "\"\n  }\n}\n",
                        StandardCharsets.UTF_8);
                }
            }

            for (CustomDefinitions.CustomBlockDefinition block : CustomDefinitions.loadBlocks(worldRoot)) {
                String blockId = sanitizeId(block.id());

                Path blockModelFile = blockModelsDir.resolve(blockId + ".json");
                Path itemModelFile = itemModelsDir.resolve(blockId + ".json");
                Path blockStateFile = blockStatesDir.resolve(blockId + ".json");

                boolean isDirectional = block.directional();

                if (Files.notExists(blockModelFile)) {
                    if (isDirectional) {
                        String modelJson = "{\n" +
                            "  \"parent\": \"minecraft:block/cube\",\n" +
                            "  \"textures\": {\n" +
                            "    \"particle\": \"radlink:block/" + blockId + "_front\",\n" +
                            "    \"north\": \"radlink:block/" + blockId + "_front\",\n" +
                            "    \"south\": \"radlink:block/" + blockId + "_back\",\n" +
                            "    \"east\": \"radlink:block/" + blockId + "_side\",\n" +
                            "    \"west\": \"radlink:block/" + blockId + "_side\",\n" +
                            "    \"up\": \"radlink:block/" + blockId + "_top\",\n" +
                            "    \"down\": \"radlink:block/" + blockId + "_top\"\n" +
                            "  }\n" +
                            "}\n";
                        Files.writeString(blockModelFile, modelJson, StandardCharsets.UTF_8);
                    } else {
                        String textureRef = Files.exists(texturesBlockDir.resolve(blockId + ".png"))
                            ? "radlink:block/" + blockId
                            : "minecraft:block/diamond_block";

                        Files.writeString(blockModelFile,
                            "{\n  \"parent\": \"minecraft:block/cube_all\",\n  \"textures\": {\n    \"all\": \"" + textureRef + "\"\n  }\n}\n",
                            StandardCharsets.UTF_8);
                    }
                }

                if (Files.notExists(itemModelFile)) {
                    Files.writeString(itemModelFile,
                        "{\n  \"parent\": \"radlink:block/" + blockId + "\"\n}\n",
                        StandardCharsets.UTF_8);
                }

                if (Files.notExists(blockStateFile)) {
                    if (isDirectional) {
                        String blockstateJson = "{\n" +
                            "  \"variants\": {\n" +
                            "    \"facing=north\": { \"model\": \"radlink:block/" + blockId + "\" },\n" +
                            "    \"facing=south\": { \"model\": \"radlink:block/" + blockId + "\", \"y\": 180 },\n" +
                            "    \"facing=west\":  { \"model\": \"radlink:block/" + blockId + "\", \"y\": 270 },\n" +
                            "    \"facing=east\":  { \"model\": \"radlink:block/" + blockId + "\", \"y\": 90 }\n" +
                            "  }\n" +
                            "}\n";
                        Files.writeString(blockStateFile, blockstateJson, StandardCharsets.UTF_8);
                    } else {
                        Files.writeString(blockStateFile,
                            "{\n  \"variants\": {\n    \"\": { \"model\": \"radlink:block/" + blockId + "\" }\n  }\n}\n",
                            StandardCharsets.UTF_8);
                    }
                }
            }

            Path dataRecipesDir = worldRoot.resolve("data").resolve(MOD_ID).resolve("recipes");
            Files.createDirectories(dataRecipesDir);

            for (CustomDefinitions.CustomItemDefinition item : CustomDefinitions.loadItems(worldRoot)) {
                String itemId = sanitizeId(item.id());
                Path recipePath = dataRecipesDir.resolve(itemId + ".json");
                if (Files.notExists(recipePath)) {
                    String recipeJson = "{\n" +
                        "  \"type\": \"minecraft:crafting_shapeless\",\n" +
                        "  \"ingredients\": [ { \"item\": \"minecraft:diamond\" } ],\n" +
                        "  \"result\": { \"item\": \"" + MOD_ID + ":" + itemId + "\", \"count\": 1 }\n" +
                        "}\n";
                    Files.writeString(recipePath, recipeJson, StandardCharsets.UTF_8);
                }
            }

            for (CustomDefinitions.CustomArmorDefinition armor : CustomDefinitions.loadArmors(worldRoot)) {
                String armorId = sanitizeId(armor.id());
                Path recipePath = dataRecipesDir.resolve(armorId + ".json");
                if (Files.notExists(recipePath)) {
                    String recipeJson = "{\n" +
                        "  \"type\": \"minecraft:crafting_shapeless\",\n" +
                        "  \"ingredients\": [ { \"item\": \"minecraft:diamond\" } ],\n" +
                        "  \"result\": { \"item\": \"" + MOD_ID + ":" + armorId + "\", \"count\": 1 }\n" +
                        "}\n";
                    Files.writeString(recipePath, recipeJson, StandardCharsets.UTF_8);
                }
            }

        } catch (IOException e) {
            throw new IllegalStateException("Failed to initialize world assets for " + worldRoot, e);
        }
    }

    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class WorldPackRegistration {
        @SubscribeEvent
        public static void addWorldResourcePack(AddPackFindersEvent event) {
            Path worldRoot = event.getPackType() == PackType.CLIENT_RESOURCES
                && StartupContentPack.getDownloadedDirectory() != null
                ? StartupContentPack.getDownloadedDirectory()
                : resolveWorldDataDir();
            if (worldRoot == null) return;

            boolean hasAssets = Files.isDirectory(worldRoot.resolve("assets").resolve(MOD_ID));
            boolean hasData = Files.isDirectory(worldRoot.resolve("data").resolve(MOD_ID));
            if (!hasAssets && !hasData) return;

            PackType requested = event.getPackType();

            if (requested == PackType.CLIENT_RESOURCES && !hasAssets) return;
            if (requested == PackType.SERVER_DATA && !hasData) return;

            event.addRepositorySource(packConsumer -> {
                PathPackResources worldPack = new PathPackResources("radlink_world_pack", false, worldRoot);

                Pack.ResourcesSupplier supplier = new Pack.ResourcesSupplier() {
                    @Override public PackResources openPrimary(String path) {return worldPack;}
                    @Override public PackResources openFull(String path, Pack.Info info) {return worldPack;}
                };

                Pack.Info info = new Pack.Info(
                    Component.literal("RadLink World Resources"),
                    PackCompatibility.COMPATIBLE,
                    FeatureFlagSet.of(),
                    List.of(),
                    false
                );

                Pack pack = Pack.create("radlink_world_pack", Component.literal("RadLink World Resources"), false,
                    supplier, info, Pack.Position.TOP, false, PackSource.DEFAULT);
                if (pack != null) {
                    packConsumer.accept(pack);
                }
            });
        }
    }

    private static String sanitizeId(String id) {
        return id.trim().toLowerCase().replace(' ', '_').replaceAll("[^a-z0-9_]", "");
    }

    private static Item createCustomItem(String itemId, CustomDefinitions.CustomItemDefinition definition) {
        String slot = definition.slot();
        if (slot == null || slot.isBlank()) {
            Item.Properties properties = new Item.Properties().stacksTo(definition.maxStackSize());
            String itemType = definition.itemType();
            boolean food = itemType != null && "food".equalsIgnoreCase(itemType.trim());
            if (itemType != null && !itemType.isBlank()) {
                if (!food) {
                    throw new IllegalArgumentException("Unsupported item_type '" + itemType
                        + "' for item '" + itemId + "'. Supported values: food.");
                }

                int nutrition = definition.nutrition() > 0 ? definition.nutrition() : 4;
                float saturation = definition.saturation() == null ? 0.3F : definition.saturation();
                properties.food(new FoodProperties.Builder()
                    .nutrition(nutrition)
                    .saturationMod(saturation)
                    .build());
            }

            return new CustomItem(itemId, properties, food, definition.trigger(), definition.ifCondition(), definition.lore());
        }
        if (isHorseArmorSlot(slot)) {
            return new CustomHorseArmorItem(itemId, definition.protection(), definition.maxStackSize(),
                definition.horseTexture(), definition.lore());
        }
        return new CustomArmorItem(itemId, createArmorMaterial(definition.material(), definition.durability(), definition.defense()),
            parseArmorType(slot), definition.maxStackSize(), definition.texture(), definition.lore());
    }

    private static boolean isHorseArmorSlot(String slot) {
        if (slot == null) return false;
        String normalized = slot.trim().toLowerCase();
        return normalized.equals("horse") || normalized.equals("horse_armor") || normalized.equals("horsearmor");
    }

    private static ArmorItem.Type parseArmorType(String slot) {
        if (slot == null || slot.isBlank()) {
            return ArmorItem.Type.HELMET;
        }
        switch (slot.trim().toLowerCase()) {
            case "head": case "helmet": return ArmorItem.Type.HELMET;
            case "chest": case "chestplate": return ArmorItem.Type.CHESTPLATE;
            case "legs": case "leg": case "leggings": return ArmorItem.Type.LEGGINGS;
            case "feet": case "foot": case "boots": return ArmorItem.Type.BOOTS;
            default:
                throw new IllegalArgumentException("Unsupported custom armor slot: " + slot
                    + ". Use head, chest, legs, feet, or horse.");
        }
    }

    private static net.minecraft.world.item.ArmorMaterial createArmorMaterial(String material, int durability, Integer defense) {
        ArmorMaterials base = parseArmorMaterial(material);
        if (durability <= 0 && defense == null) return base;
        return new CustomArmorMaterial(base, durability, defense);
    }

    private static ArmorMaterials parseArmorMaterial(String material) {
        if (material == null) return ArmorMaterials.LEATHER;
        switch (material.toLowerCase()) {
            case "leather": return ArmorMaterials.LEATHER;
            case "iron": return ArmorMaterials.IRON;
            case "chain": case "chainmail": return ArmorMaterials.CHAIN;
            case "gold": case "golden": return ArmorMaterials.GOLD;
            case "diamond": return ArmorMaterials.DIAMOND;
            case "netherite": return ArmorMaterials.NETHERITE;
            default:
                try {
                    return ArmorMaterials.valueOf(material.toUpperCase());
                } catch (Exception e) {
                    return ArmorMaterials.LEATHER;
                }
        }
    }
}