package org.Lyosha008.radlink;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.HashMap;
import java.util.Map;

@Mod.EventBusSubscriber(modid = Radlink.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CustomCommandLoader extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new Gson();
    public static final Map<String, JsonObject> LOADED_COMMANDS = new HashMap<>();
    private final CommandDispatcher<CommandSourceStack> dispatcher;

    public CustomCommandLoader(CommandDispatcher<CommandSourceStack> dispatcher) {
        super(GSON, "commands");
        this.dispatcher = dispatcher;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement>objectMap,ResourceManager resourceManager,ProfilerFiller profiler){
        LOADED_COMMANDS.clear();
        objectMap.forEach((location, jsonElement) -> {
            if (jsonElement.isJsonObject()) {
                String commandName = location.getPath();
                LOADED_COMMANDS.put(commandName, jsonElement.getAsJsonObject());
                System.out.println("[RadLink Debug] Загружена команда из JSON: " + commandName);
            }
        });
        CustomCommandRegistry.registerCustomCommands(dispatcher);
    }

    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        ReloadableServerResources serverResources = event.getServerResources();
        event.addListener(new CustomCommandLoader(serverResources.getCommands().getDispatcher()));
    }
}