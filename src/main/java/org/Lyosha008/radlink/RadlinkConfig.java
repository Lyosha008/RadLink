package org.Lyosha008.radlink;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

public class RadlinkConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.ConfigValue<String> SERVER_OR_WORLD_NAME;
    public static final ForgeConfigSpec.BooleanValue IS_CREATIVE_TAB;
    public static final ForgeConfigSpec.BooleanValue IS_MENU_CHANGE;

    static {
        BUILDER.push("Menu Settings");
        IS_MENU_CHANGE = BUILDER
            .comment("Allow changing the main menu background through JSON/Texture")
            .define("isMenuChange", false);

        SERVER_OR_WORLD_NAME = BUILDER
            .comment("The world name or IP of the server to load the menu background from.\n" +
                "You can specify the world folder name (e.g., 'New World') or the server IP (e.g., 'play.example.com').\n" +
                "Leave blank to automatically select the last save.")
            .define("serverOrWorldName", "");
        BUILDER.pop();
    }

    static {
        BUILDER.push("General Settings");
        IS_CREATIVE_TAB = BUILDER
            .comment("Enable custom creative tab for mod items and blocks")
            .define("isCreativeTab", false);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    public static void register() {ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, SPEC);}
}