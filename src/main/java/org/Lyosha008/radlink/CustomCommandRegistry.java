package org.Lyosha008.radlink;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Radlink.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CustomCommandRegistry {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

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
    }

    static void registerCustomCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        CustomCommandLoader.LOADED_COMMANDS.forEach((commandName, json) -> {
            int permLevel = json.has("permission_level") ? json.get("permission_level").getAsInt() : 0;
            var command = Commands.literal(commandName)
                .requires(source -> source.hasPermission(permLevel))
                .executes(context -> execute(json, context.getSource(), context.getSource().getEntity()));

            if (json.has("arguments") && json.get("arguments").isJsonArray()) {
                for (JsonElement argumentElement : json.getAsJsonArray("arguments")) {
                    if (!argumentElement.isJsonObject()) {
                        continue;
                    }
                    JsonObject argument = argumentElement.getAsJsonObject();
                    if (!argument.has("name") || !argument.has("type")
                        || !"player".equalsIgnoreCase(argument.get("type").getAsString())) {
                        continue;
                    }

                    String argumentName = argument.get("name").getAsString();
                    command.then(Commands.argument(argumentName, EntityArgument.player())
                        .executes(context -> execute(
                            json,
                            context.getSource(),
                            EntityArgument.getPlayer(context, argumentName)
                        )));
                    break;
                }
            }

            dispatcher.register(command);
        });
    }

    private static int execute(JsonObject command, CommandSourceStack source, Entity entity) {
        if (command.has("actions")) {
            JsonElement actions = command.get("actions");
            if (actions.isJsonArray()) {
                for (JsonElement action : actions.getAsJsonArray()) {
                    executeAction(action, source, entity);
                }
            } else {
                executeAction(actions, source, entity);
            }
        }
        return 1;
    }

    private static void executeAction(JsonElement action, CommandSourceStack source, Entity entity) {
        if (action.isJsonObject()) {
            JsonObject actionObject = action.getAsJsonObject();
            if (actionObject.has("type")
                && "chat_message".equalsIgnoreCase(actionObject.get("type").getAsString())
                && actionObject.has("say")) {
                source.sendSuccess(() -> Component.literal(actionObject.get("say").getAsString()), false);
                return;
            }
        }

        ScriptEngine.executeActions(
            action,
            source.getLevel(),
            BlockPos.containing(source.getPosition()),
            entity
        );
    }
}