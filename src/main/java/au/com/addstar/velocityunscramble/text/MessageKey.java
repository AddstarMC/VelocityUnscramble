package au.com.addstar.velocityunscramble.text;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every player-facing message, with the path it lives under in messages.yml and
 * the built-in default used when the file does not define it.
 *
 * <p>The default is also what gets written into the shipped template, so adding
 * a message means adding a constant here and a line in messages.yml.
 *
 * <p>Placeholders are MiniMessage tags: a message may use any tag listed in its
 * {@link #placeholders()}, or omit them entirely. {@code <prefix>} is available
 * in every message.
 */
public enum MessageKey {
    // --- game lifecycle ---
    /**
     * The announcement line. The word itself follows on {@link #GAME_START_WORD}
     * so the monospace font can do its job without the sentence pushing it around.
     */
    GAME_START("game.start",
            "<prefix><yellow>New Game! Unscramble this:",
            "word"),
    /** The scrambled word, on its own line and in the custom monospace font. */
    GAME_START_WORD("game.start-word",
            "<light_purple><bold>></bold> <gold><font:" + Fonts.MONO + "><word>",
            "word"),
    GAME_PRIZE("game.prize",
            "<prefix><yellow>The prize for winning is <gold><points> point<plural>",
            "points", "plural"),
    GAME_TIME_LEFT("game.time-left",
            "<prefix><yellow><time>",
            "time"),
    GAME_HINT("game.hint",
            "<prefix><yellow>Hint!... <gold><font:" + Fonts.MONO + "><hint>",
            "hint"),
    GAME_REMINDER("game.reminder",
            "<prefix><yellow>Again, the word was... <gold><font:" + Fonts.MONO + "><word>",
            "word"),
    GAME_TIMED_OUT("game.timed-out",
            "<prefix><yellow>Oh! Sorry, you didnt get the word in time!"),
    GAME_CANCELLED("game.cancelled",
            "<prefix><yellow>Oh! Sorry, the game was cancelled."),
    GAME_ANSWER_WAS("game.answer-was",
            "<prefix><yellow>The answer was... <red><font:" + Fonts.MONO + "><word>",
            "word"),
    GAME_STARTING_SOON("game.starting-soon",
            "<prefix><yellow>A new game will start in <gold><seconds></gold> seconds!",
            "seconds"),

    // --- time formatting, used to build <time> in game.time-left ---
    TIME_MINUTE("time.minute", "1 Minute"),
    TIME_MINUTES("time.minutes", "<count> Minutes", "count"),
    TIME_SECOND("time.second", "1 Second"),
    TIME_SECONDS("time.seconds", "<count> Seconds", "count"),
    TIME_SUFFIX("time.suffix", "<time> Left", "time"),

    // --- winning ---
    WIN_BROADCAST("win.broadcast",
            "<prefix><yellow>Congratulations <aqua><player></aqua>!",
            "player", "word", "points", "duration"),
    WIN_POINTS("win.points",
            "<prefix><yellow>You now have <gold><points></gold> unscramble points.",
            "points", "awarded", "wins", "total"),
    WIN_CLAIM_HINT("win.claim-hint",
            "<prefix><light_purple>Use your points to get rewards at <aqua>/rewards"),
    WIN_STATS_HINT("win.stats-hint",
            "<prefix><light_purple>Type <aqua>/us stats</aqua> to view your unscramble stats."),
    WIN_SAVE_FAILED("win.save-failed",
            "<prefix><red>Your win could not be saved. Please notify an admin."),
    WIN_TOO_MANY_CAPS("win.too-many-caps",
            "<prefix><yellow>Answer rejected: <red>too many caps"),

    // --- stats ---
    STATS_HEADER("stats.header",
            "<prefix><yellow>Unscramble stats for <aqua><player></aqua>:",
            "player"),
    STATS_LINE("stats.line",
            "<prefix><yellow>Wins: <gold><wins></gold>  Points: <gold><points></gold>  "
                    + "Total earned: <gold><total></gold>",
            "player", "wins", "points", "total"),
    STATS_PLAYERS_ONLY("stats.players-only",
            "<prefix><red>Only players have stats."),
    STATS_FAILED("stats.failed",
            "<prefix><red>Could not read your stats. Please try again later."),

    // --- commands ---
    COMMAND_GAME_RUNNING("command.game-running",
            "<prefix><red>A game is already running."),
    COMMAND_NO_GAME("command.no-game",
            "<prefix><red>There is no game running right now."),
    COMMAND_START_FAILED("command.start-failed",
            "<prefix><red>Could not start the game. Check the console for details."),
    COMMAND_RELOADED("command.reloaded",
            "<prefix><green>Configuration reloaded."),
    COMMAND_RELOAD_FAILED("command.reload-failed",
            "<prefix><red>Reload failed: <message>",
            "message"),
    COMMAND_DEBUG_TOGGLED("command.debug-toggled",
            "<prefix><green>Debug mode is now <state>",
            "state"),
    COMMAND_DEBUG_ENABLED("command.debug-enabled", "enabled"),
    COMMAND_DEBUG_DISABLED("command.debug-disabled", "disabled"),
    COMMAND_CLAIM_RETIRED("command.claim-retired",
            "<prefix><yellow>Prizes are now awarded automatically as points. "
                    + "Use <aqua>/us stats</aqua> to see your total."),
    COMMAND_GUESS_PLAYERS_ONLY("command.guess-players-only",
            "<prefix><red>Only players can guess."),

    // --- help ---
    HELP_HEADER("help.header",
            "<dark_purple>=========<red> [ Unscramble Help ] <dark_purple>========="),
    HELP_HELP("help.help",
            "<red>/unscramble <gray>help <yellow>- Shows this screen"),
    HELP_STATS("help.stats",
            "<red>/unscramble <gray>stats <yellow>- Show your unscramble stats"),
    HELP_GUESS("help.guess",
            "<red>/guess <gray><word> <yellow>- Submit an answer"),
    HELP_RELOAD("help.reload",
            "<red>/unscramble <gray>reload <yellow>- Reloads the config"),
    HELP_HINT("help.hint",
            "<red>/unscramble <gray>hint <yellow>- Gives a hint on the current word"),
    HELP_CANCEL("help.cancel",
            "<red>/unscramble <gray>cancel <yellow>- Cancels the running game"),
    HELP_NEWGAME("help.newgame",
            "<red>/unscramble <gray>newgame [word] [time] [hint-interval] [hint-chars] "
                    + "<yellow>- Starts a new game"),
    HELP_NEWGAME_NOTE("help.newgame-note",
            "<green> Underscores (_) in the word become spaces"),
    HELP_FOOTER("help.footer",
            "<dark_purple>=====================================");

    /**
     * Holder for constants used inside the enum constants above. A plain static
     * field of the enum cannot be read there: enum constants are initialised
     * first, so the field would still be null.
     */
    private static final class Fonts {
        /**
         * The custom monospace font from the AddstarMC resource pack. Every
         * letter is the same width in it, so a scrambled word cannot be read
         * from letter spacing and the hint asterisks line up under it.
         *
         * <p>A client without the pack falls back to the default font and just
         * sees proportional text, so nothing breaks either way.
         */
        static final String MONO = "addstarmc:mono";

        private Fonts() {
        }
    }

    /** The custom monospace font used for the scrambled word and hints. */
    public static final String FONT_MONO = Fonts.MONO;

    private static final Map<String, MessageKey> BY_PATH = new LinkedHashMap<>();

    static {
        for (MessageKey key : values()) {
            BY_PATH.put(key.path, key);
        }
    }

    private final String path;
    private final String defaultValue;
    private final String[] placeholders;

    MessageKey(String path, String defaultValue, String... placeholders) {
        this.path = path;
        this.defaultValue = defaultValue;
        this.placeholders = placeholders;
    }

    /** Dotted path in messages.yml, e.g. {@code game.hint}. */
    public String path() {
        return path;
    }

    /** The built-in text, used when messages.yml has no entry for this key. */
    public String defaultValue() {
        return defaultValue;
    }

    /** Placeholder names this message may use, without the angle brackets. */
    public String[] placeholders() {
        return Arrays.copyOf(placeholders, placeholders.length);
    }

    /** Path segments, for addressing the node in Configurate. */
    public Object[] nodePath() {
        return path.split("\\.");
    }

    public static Map<String, MessageKey> byPath() {
        return Map.copyOf(BY_PATH);
    }
}
