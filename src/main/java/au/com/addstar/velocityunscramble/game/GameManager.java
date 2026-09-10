package au.com.addstar.velocityunscramble.game;

import au.com.addstar.velocityunscramble.VelocityUnscramblePlugin;
import au.com.addstar.velocityunscramble.config.ConfigManager;
import au.com.addstar.velocityunscramble.db.Database;
import au.com.addstar.velocityunscramble.text.MessageKey;
import au.com.addstar.velocityunscramble.text.MessageStore;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import com.velocitypowered.api.scheduler.Scheduler;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

/** Owns the single active session and the auto-game timer. */
public class GameManager {
    private final VelocityUnscramblePlugin plugin;
    private final ProxyServer proxy;
    private final Logger logger;
    private final ConfigManager config;
    private final Database database;
    private final Random random = new Random();

    private final AtomicReference<Session> current = new AtomicReference<>();
    private volatile ScheduledTask autoGameTask;
    private volatile boolean debug;

    public GameManager(VelocityUnscramblePlugin plugin, ProxyServer proxy, Logger logger,
                       ConfigManager config, Database database) {
        this.plugin = plugin;
        this.proxy = proxy;
        this.logger = logger;
        this.config = config;
        this.database = database;
        this.debug = config.main().debugEnabled;
    }

    public VelocityUnscramblePlugin plugin() {
        return plugin;
    }

    public ProxyServer proxy() {
        return proxy;
    }

    public Scheduler scheduler() {
        return proxy.getScheduler();
    }

    public Logger logger() {
        return logger;
    }

    public ConfigManager config() {
        return config;
    }

    public Database database() {
        return database;
    }

    public MessageStore messages() {
        return config.messages();
    }

    public Session currentSession() {
        return current.get();
    }

    public boolean isSessionRunning() {
        return current.get() != null;
    }

    public boolean isDebug() {
        return debug;
    }

    public void setDebug(boolean value) {
        this.debug = value;
    }

    public void debug(String message) {
        if (debug) {
            logger.info(message);
        }
    }

    public void broadcast(Component component) {
        proxy.sendMessage(component);
    }

    /**
     * Broadcasts a configured message. A message an administrator has blanked
     * out is skipped rather than sent as an empty line.
     */
    public void broadcast(MessageKey key, Object... placeholders) {
        MessageStore messages = messages();
        if (!messages.isBlank(key)) {
            proxy.sendMessage(messages.get(key, placeholders));
        }
    }

    /** Sends a configured message to one recipient, skipping it if blanked out. */
    public void send(CommandSource target, MessageKey key, Object... placeholders) {
        MessageStore messages = messages();
        if (!messages.isBlank(key)) {
            target.sendMessage(messages.get(key, placeholders));
        }
    }

    public void onSessionFinish() {
        current.set(null);
    }

    /** Starts a game. Returns false if one is already running or the word failed to scramble. */
    public boolean newSession(String word, long length, long hintInterval, int hintChars) {
        String chosen = (word == null || word.isEmpty()) ? randomWord() : word;
        Session session = new Session(this, random, chosen, length, hintInterval, hintChars);

        if (!session.isValid()) {
            logger.warn("Invalid game, aborted!");
            return false;
        }
        if (!current.compareAndSet(null, session)) {
            return false;
        }
        session.start();
        return true;
    }

    /** Starts an auto game using auto.yml timings and the shared word list. */
    public void startAutoGame() {
        var game = config.game();
        newSession(randomWord(),
                TimeUnit.SECONDS.toMillis(game.length),
                TimeUnit.SECONDS.toMillis(game.hintInterval),
                game.hintChars);
    }

    /** Picks a word from random-words in config.yml, the single word pool. */
    public String randomWord() {
        var words = config.main().words;
        return words.isEmpty() ? "unscramble" : words.get(random.nextInt(words.size()));
    }

    /** Awards the win: announce, persist, then report the new total. */
    void awardWin(Player player, Session session, double duration) {
        broadcast(MessageKey.WIN_BROADCAST,
                "player", player.getUsername(),
                "word", session.word(),
                "points", session.points(),
                "duration", duration);

        database.recordWin(player.getUniqueId(), session.word(), session.difficulty(),
                        session.points(), duration)
                .thenAccept(record -> {
                    send(player, MessageKey.WIN_POINTS,
                            "points", record.points(),
                            "awarded", session.points(),
                            "wins", record.wins(),
                            "total", record.totalPoints());

                    if (record.wins() <= 5 || record.points() % 25 == 0) {
                        send(player, MessageKey.WIN_CLAIM_HINT);
                        send(player, MessageKey.WIN_STATS_HINT);
                    }
                    logger.info("Awarded {} point(s) to {}", session.points(),
                            player.getUsername());
                })
                .exceptionally(error -> {
                    logger.error("Could not record win for {}", player.getUsername(), error);
                    send(player, MessageKey.WIN_SAVE_FAILED);
                    return null;
                });
    }


    // --- auto game scheduling ---

    public void reloadAutoGame() {
        cancelAutoGame();
        if (config.main().autoGameEnabled) {
            debug("Starting AutoGame timer. Will only run with at least "
                    + config.game().minPlayers + " players online.");
            scheduleNextGame();
        }
    }

    public void cancelAutoGame() {
        ScheduledTask task = autoGameTask;
        if (task != null) {
            task.cancel();
        }
        autoGameTask = null;
    }

    /** Schedules the next auto game, skewed by a random offset. */
    public void scheduleNextGame() {
        cancelAutoGame();

        var game = config.game();
        int offsetRange = Math.max(game.randomOffset * 60, 1);
        int offsetSecs = random.nextInt(offsetRange * 2) - offsetRange;
        long delay = Math.max((long) game.interval * 60 + offsetSecs, 1);

        debug("Next AutoGame will start in " + delay + " seconds.");

        autoGameTask = scheduler()
                .buildTask(plugin, new AutoGameStarter(this, game.warningPeriod, game.minPlayers))
                .delay(delay, TimeUnit.SECONDS)
                .schedule();
    }
}
