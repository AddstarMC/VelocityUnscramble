package au.com.addstar.velocityunscramble;

import au.com.addstar.velocityunscramble.command.GuessCommand;
import au.com.addstar.velocityunscramble.command.UnscrambleCommand;
import au.com.addstar.velocityunscramble.config.ConfigManager;
import au.com.addstar.velocityunscramble.db.Database;
import au.com.addstar.velocityunscramble.game.GameManager;
import au.com.addstar.velocityunscramble.listener.ChatListener;
import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import java.io.IOException;
import java.nio.file.Path;
import org.slf4j.Logger;

@Plugin(
        id = "unscramble",
        name = "VelocityUnscramble",
        version = BuildConstants.VERSION,
        description = "Word unscramble game for the proxy, awarding points to winners.",
        url = "https://github.com/AddstarMC/VelocityUnscramble",
        authors = {"AddstarMC", "Schmoller"}
)
public class VelocityUnscramblePlugin {
    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;

    private ConfigManager config;
    private Database database;
    private GameManager gameManager;

    @Inject
    public VelocityUnscramblePlugin(ProxyServer proxy, Logger logger,
                                    @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    public Logger logger() {
        return logger;
    }

    public GameManager gameManager() {
        return gameManager;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        config = new ConfigManager(dataDirectory, logger);
        try {
            config.load();
        } catch (IOException e) {
            logger.error("Could not load configuration; the plugin will not start.", e);
            return;
        }

        database = new Database(config.main().dbURL, config.main().dbUsername,
                config.main().dbPassword, logger);
        database.initSchema().exceptionally(error -> {
            logger.error("Could not initialise the database schema.", error);
            return null;
        });

        gameManager = new GameManager(this, proxy, logger, config, database);

        proxy.getEventManager().register(this, new ChatListener(gameManager));

        CommandManager commands = proxy.getCommandManager();
        CommandMeta meta = commands.metaBuilder("unscramble")
                .aliases("us")
                .plugin(this)
                .build();
        commands.register(meta, UnscrambleCommand.create(this, gameManager));

        CommandMeta guessMeta = commands.metaBuilder("guess").plugin(this).build();
        commands.register(guessMeta, GuessCommand.create(gameManager));

        gameManager.reloadAutoGame();
        logger.info("VelocityUnscramble {} enabled.", BuildConstants.VERSION);
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (gameManager != null) {
            var session = gameManager.currentSession();
            if (session != null) {
                session.stop();
            }
            gameManager.cancelAutoGame();
        }
        if (database != null) {
            database.close();
        }
    }

    /** Reloads config and restarts the auto-game timer. Used by /us reload. */
    public void reload() throws IOException {
        config.load();
        gameManager.setDebug(config.main().debugEnabled);
        gameManager.reloadAutoGame();
    }
}
