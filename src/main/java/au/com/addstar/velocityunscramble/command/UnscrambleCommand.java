package au.com.addstar.velocityunscramble.command;

import au.com.addstar.velocityunscramble.VelocityUnscramblePlugin;
import au.com.addstar.velocityunscramble.game.GameManager;
import au.com.addstar.velocityunscramble.game.Session;
import au.com.addstar.velocityunscramble.text.MessageKey;
import au.com.addstar.velocityunscramble.text.Messages;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import java.util.concurrent.TimeUnit;

/** /unscramble (alias /us). */
public final class UnscrambleCommand {
    private static final long DEFAULT_LENGTH_SECONDS = 30;
    private static final long DEFAULT_HINT_INTERVAL_SECONDS = 12;
    private static final int DEFAULT_HINT_CHARS = 2;

    private UnscrambleCommand() {
    }

    public static BrigadierCommand create(VelocityUnscramblePlugin plugin, GameManager manager) {
        LiteralArgumentBuilder<CommandSource> node = BrigadierCommand
                .literalArgumentBuilder("unscramble")
                .executes(ctx -> help(manager, ctx.getSource()))
                .then(BrigadierCommand.literalArgumentBuilder("help")
                        .executes(ctx -> help(manager, ctx.getSource())))
                .then(BrigadierCommand.literalArgumentBuilder("stats")
                        .executes(ctx -> stats(manager, ctx.getSource())))
                .then(BrigadierCommand.literalArgumentBuilder("points")
                        .executes(ctx -> stats(manager, ctx.getSource())))
                .then(BrigadierCommand.literalArgumentBuilder("claim")
                        .executes(ctx -> {
                            manager.send(ctx.getSource(), MessageKey.COMMAND_CLAIM_RETIRED);
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(BrigadierCommand.literalArgumentBuilder("reload")
                        .requires(src -> src.hasPermission("unscramble.reload"))
                        .executes(ctx -> reload(plugin, manager, ctx.getSource())))
                .then(BrigadierCommand.literalArgumentBuilder("hint")
                        .requires(src -> src.hasPermission("unscramble.hint"))
                        .executes(ctx -> hint(manager, ctx.getSource())))
                .then(BrigadierCommand.literalArgumentBuilder("cancel")
                        .requires(src -> src.hasPermission("unscramble.cancel"))
                        .executes(ctx -> cancel(manager, ctx.getSource())))
                .then(BrigadierCommand.literalArgumentBuilder("debug")
                        .requires(src -> src.hasPermission("unscramble.debug"))
                        .executes(ctx -> {
                            manager.setDebug(!manager.isDebug());
                            manager.send(ctx.getSource(), MessageKey.COMMAND_DEBUG_TOGGLED,
                                    "state", Messages.plain(manager.messages().get(
                                            manager.isDebug()
                                                    ? MessageKey.COMMAND_DEBUG_ENABLED
                                                    : MessageKey.COMMAND_DEBUG_DISABLED)));
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(newGameNode(manager));

        return new BrigadierCommand(node);
    }

    private static LiteralArgumentBuilder<CommandSource> newGameNode(GameManager manager) {
        return BrigadierCommand.literalArgumentBuilder("newgame")
                .requires(src -> src.hasPermission("unscramble.newgame"))
                .executes(ctx -> startGame(manager, ctx.getSource(), "",
                        DEFAULT_LENGTH_SECONDS, DEFAULT_HINT_INTERVAL_SECONDS,
                        DEFAULT_HINT_CHARS))
                .then(BrigadierCommand.requiredArgumentBuilder("word", StringArgumentType.word())
                        .executes(ctx -> startGame(manager, ctx.getSource(), word(ctx),
                                DEFAULT_LENGTH_SECONDS, DEFAULT_HINT_INTERVAL_SECONDS,
                                DEFAULT_HINT_CHARS))
                        .then(BrigadierCommand.requiredArgumentBuilder("time", IntegerArgumentType.integer(1))
                                .executes(ctx -> startGame(manager, ctx.getSource(), word(ctx),
                                        IntegerArgumentType.getInteger(ctx, "time"),
                                        DEFAULT_HINT_INTERVAL_SECONDS, DEFAULT_HINT_CHARS))
                                .then(BrigadierCommand.requiredArgumentBuilder("hint-interval", IntegerArgumentType.integer(0))
                                        .executes(ctx -> startGame(manager, ctx.getSource(), word(ctx),
                                                IntegerArgumentType.getInteger(ctx, "time"),
                                                IntegerArgumentType.getInteger(ctx, "hint-interval"),
                                                DEFAULT_HINT_CHARS))
                                        .then(BrigadierCommand.requiredArgumentBuilder("hint-chars", IntegerArgumentType.integer(1))
                                                .executes(ctx -> startGame(manager, ctx.getSource(), word(ctx),
                                                        IntegerArgumentType.getInteger(ctx, "time"),
                                                        IntegerArgumentType.getInteger(ctx, "hint-interval"),
                                                        IntegerArgumentType.getInteger(ctx, "hint-chars")))))));
    }

    /** Underscores stand in for spaces, so a phrase fits in one argument. */
    private static String word(CommandContext<CommandSource> ctx) {
        return StringArgumentType.getString(ctx, "word").replace('_', ' ');
    }


    private static int startGame(GameManager manager, CommandSource source, String word,
                                 long timeSeconds, long hintIntervalSeconds, int hintChars) {
        if (manager.isSessionRunning()) {
            manager.send(source, MessageKey.COMMAND_GAME_RUNNING);
            return Command.SINGLE_SUCCESS;
        }

        boolean started = manager.newSession(word,
                TimeUnit.SECONDS.toMillis(timeSeconds),
                TimeUnit.SECONDS.toMillis(hintIntervalSeconds),
                hintChars);

        if (!started) {
            manager.send(source, MessageKey.COMMAND_START_FAILED);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int help(GameManager manager, CommandSource source) {
        manager.send(source, MessageKey.HELP_HEADER);
        manager.send(source, MessageKey.HELP_HELP);
        manager.send(source, MessageKey.HELP_STATS);
        manager.send(source, MessageKey.HELP_GUESS);
        if (source.hasPermission("unscramble.reload")) {
            manager.send(source, MessageKey.HELP_RELOAD);
        }
        if (source.hasPermission("unscramble.hint")) {
            manager.send(source, MessageKey.HELP_HINT);
        }
        if (source.hasPermission("unscramble.cancel")) {
            manager.send(source, MessageKey.HELP_CANCEL);
        }
        if (source.hasPermission("unscramble.newgame")) {
            manager.send(source, MessageKey.HELP_NEWGAME);
            manager.send(source, MessageKey.HELP_NEWGAME_NOTE);
        }
        manager.send(source, MessageKey.HELP_FOOTER);
        return Command.SINGLE_SUCCESS;
    }

    private static int stats(GameManager manager, CommandSource source) {
        if (!(source instanceof Player player)) {
            manager.send(source, MessageKey.STATS_PLAYERS_ONLY);
            return Command.SINGLE_SUCCESS;
        }

        manager.database().getRecord(player.getUniqueId())
                .thenAccept(record -> {
                    manager.send(player, MessageKey.STATS_HEADER,
                            "player", player.getUsername());
                    manager.send(player, MessageKey.STATS_LINE,
                            "player", player.getUsername(),
                            "wins", record.wins(),
                            "points", record.points(),
                            "total", record.totalPoints());
                })
                .exceptionally(error -> {
                    manager.logger().error("Could not read stats for {}",
                            player.getUsername(), error);
                    manager.send(player, MessageKey.STATS_FAILED);
                    return null;
                });
        return Command.SINGLE_SUCCESS;
    }

    private static int reload(VelocityUnscramblePlugin plugin, GameManager manager,
                              CommandSource source) {
        try {
            plugin.reload();
            // Sent after the reload, so it uses the newly loaded wording.
            manager.send(source, MessageKey.COMMAND_RELOADED);
        } catch (Exception e) {
            plugin.logger().error("Reload failed", e);
            manager.send(source, MessageKey.COMMAND_RELOAD_FAILED,
                    "message", String.valueOf(e.getMessage()));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int hint(GameManager manager, CommandSource source) {
        Session session = manager.currentSession();
        if (session == null) {
            manager.send(source, MessageKey.COMMAND_NO_GAME);
        } else {
            session.doHint();
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int cancel(GameManager manager, CommandSource source) {
        Session session = manager.currentSession();
        if (session == null) {
            manager.send(source, MessageKey.COMMAND_NO_GAME);
        } else {
            session.stop();
        }
        return Command.SINGLE_SUCCESS;
    }
}
