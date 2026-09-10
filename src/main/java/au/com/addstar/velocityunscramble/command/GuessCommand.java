package au.com.addstar.velocityunscramble.command;

import au.com.addstar.velocityunscramble.game.GameManager;
import au.com.addstar.velocityunscramble.game.Session;
import au.com.addstar.velocityunscramble.text.MessageKey;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;

/**
 * Explicit alternative to guessing in chat. Useful where a chat plugin eats the
 * message, and the fallback if proxy chat reading ever stops being viable.
 */
public final class GuessCommand {
    private GuessCommand() {
    }

    public static BrigadierCommand create(GameManager manager) {
        LiteralArgumentBuilder<CommandSource> node = BrigadierCommand
                .literalArgumentBuilder("guess")
                .then(BrigadierCommand.requiredArgumentBuilder("word", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            CommandSource source = ctx.getSource();
                            if (!(source instanceof Player player)) {
                                manager.send(source, MessageKey.COMMAND_GUESS_PLAYERS_ONLY);
                                return com.mojang.brigadier.Command.SINGLE_SUCCESS;
                            }
                            Session session = manager.currentSession();
                            if (session == null) {
                                manager.send(source, MessageKey.COMMAND_NO_GAME);
                                return com.mojang.brigadier.Command.SINGLE_SUCCESS;
                            }
                            session.makeGuess(player, StringArgumentType.getString(ctx, "word"));
                            return com.mojang.brigadier.Command.SINGLE_SUCCESS;
                        }));

        return new BrigadierCommand(node);
    }
}
