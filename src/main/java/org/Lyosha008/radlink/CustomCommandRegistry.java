package org.Lyosha008.radlink;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Radlink.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CustomCommandRegistry {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        // 1. Команда перезагрузки
        dispatcher.register(
            Commands.literal("radlink")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("reload")
                    .executes(context -> {
                        context.getSource().getServer().getCommands().performPrefixedCommand(
                            context.getSource(), "reload"
                        );
                        context.getSource().sendSuccess(
                            () -> Component.literal("§a[RadLink] Команды обновлены!"), true
                        );
                        return 1;
                    })
                )
        );

        // 2. Регистрация кастомных команд из JSON
        CustomCommandLoader.LOADED_COMMANDS.forEach((commandName, json) -> {
            int permLevel = json.has("permission_level") ? json.get("permission_level").getAsInt() : 0;

            dispatcher.register(
                Commands.literal(commandName)
                    .requires(source -> source.hasPermission(permLevel))
                    .executes(context -> {
                        CommandSourceStack source = context.getSource();

                        // Передаем actions прямо в ваш ScriptEngine
                        if (json.has("actions")) {
                            ScriptEngine.executeActions(
                                json.get("actions"),
                                source.getLevel(),
                                BlockPos.containing(source.getPosition()),
                                source.getEntity()
                            );
                        }
                        return 1;
                    })
            );
        });
    }
}