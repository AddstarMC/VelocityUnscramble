package au.com.addstar.velocityunscramble.config;

import au.com.addstar.velocityunscramble.text.MessageConfig;
import au.com.addstar.velocityunscramble.text.MessageStore;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

/**
 * Loads config.yml, auto.yml and messages.yml.
 *
 * <p>Existing files are read but never written back. Configurate's YAML backend
 * cannot round-trip comments - it neither reads nor writes them - so saving over
 * a live file would strip every comment and blank line in it. First run copies
 * the commented templates from the jar instead; after that the files belong to
 * the administrator. A key added by a later update falls back to its default
 * and is logged, so it can be added by hand without anything being clobbered.
 */
public class ConfigManager {
    private final Path dataDir;
    private final Logger logger;

    private MainConfig main = new MainConfig();
    private GameConfig game = new GameConfig();
    private volatile MessageStore messages = MessageStore.defaults();

    public ConfigManager(Path dataDir, Logger logger) {
        this.dataDir = dataDir;
        this.logger = logger;
    }

    public MainConfig main() {
        return main;
    }

    public GameConfig game() {
        return game;
    }

    /**
     * The current messages. Held as a whole immutable object so a reload swaps
     * the set atomically and a running game never sees a partial one.
     */
    public MessageStore messages() {
        return messages;
    }

    public void load() throws IOException {
        Files.createDirectories(dataDir);
        main = load("config.yml", MainConfig.class, new MainConfig());
        game = load("auto.yml", GameConfig.class, new GameConfig());
        // After main: a retired claim-message there still feeds win.claim-hint.
        messages = loadMessages(main.claimMessage);
    }

    /**
     * messages.yml is read as a plain node tree rather than mapped onto a class:
     * the keys are a flat, growing list of strings, and this way a message the
     * file does not define simply falls back to its built-in default.
     */
    private MessageStore loadMessages(String legacyClaimMessage) throws IOException {
        Path file = dataDir.resolve(MessageConfig.FILE_NAME);
        boolean firstRun = !Files.exists(file);
        if (firstRun) {
            copyTemplate(MessageConfig.FILE_NAME, file);
        }

        // On the run that creates messages.yml, its win.claim-hint is only the
        // shipped default, so a claim-message already in config.yml should still
        // win. Once the administrator edits messages.yml, that file decides.
        String legacy = firstRun ? legacyClaimMessage : null;
        if (!firstRun && legacyClaimMessage != null && !legacyClaimMessage.isEmpty()) {
            logger.warn("config.yml still sets the retired 'claim-message'. It is ignored; "
                    + "'{}' in {} is what players see now.",
                    MessageConfig.CLAIM_HINT_PATH, MessageConfig.FILE_NAME);
        }

        if (!Files.exists(file)) {
            return MessageStore.defaults();
        }

        try {
            return MessageConfig.from(loader(file).load(), logger, legacy);
        } catch (ConfigurateException e) {
            throw new IOException("Could not read " + MessageConfig.FILE_NAME, e);
        }
    }

    private <T> T load(String name, Class<T> type, T fallback) throws IOException {
        Path file = dataDir.resolve(name);
        if (!Files.exists(file)) {
            copyTemplate(name, file);
        }

        try {
            CommentedConfigurationNode root = loader(file).load();
            // Snapshot the keys the file actually declares: get() fills missing
            // nodes in as a side effect, which would hide them from the check.
            Set<Object> declared = new HashSet<>(root.childrenMap().keySet());
            T value = root.get(type, fallback);
            warnAboutMissingKeys(declared, root, type, value, name);
            return value;
        } catch (ConfigurateException e) {
            throw new IOException("Could not read " + name, e);
        }
    }

    private static YamlConfigurationLoader loader(Path file) {
        return YamlConfigurationLoader.builder()
                .path(file)
                .nodeStyle(NodeStyle.BLOCK)
                .indent(2)
                .build();
    }

    /** Writes the commented template shipped in the jar. */
    private void copyTemplate(String name, Path target) throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(name)) {
            if (in == null) {
                logger.warn("No bundled template for {}; starting from built-in defaults.", name);
                return;
            }
            Files.copy(in, target);
            logger.info("Wrote default {}", name);
        }
    }

    /**
     * Reports top-level keys the file does not define. They fall back to
     * defaults; the file is left alone so its comments stay intact.
     */
    private <T> void warnAboutMissingKeys(Set<Object> declared, ConfigurationNode existing,
                                          Class<T> type, T value, String name)
            throws ConfigurateException {
        CommentedConfigurationNode complete = CommentedConfigurationNode.root(existing.options());
        complete.set(type, value);

        for (ConfigurationNode child : complete.childrenMap().values()) {
            Object key = child.key();
            if (key != null && !declared.contains(key)) {
                logger.warn("{} has no '{}' entry; using the default. "
                        + "Add it to the file to set it explicitly.", name, key);
            }
        }
    }
}
