package org.AtomLink.radlink;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

public class RadlinkConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue IS_CREATIVE_TAB;

    static {
        BUILDER.push("General Settings");
        IS_CREATIVE_TAB = BUILDER
            .comment("Enable custom creative tab for mod items and blocks")
            .define("isCreativeTab", false); // По умолчанию false
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SPEC);
    }
}