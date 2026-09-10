package au.com.addstar.velocityunscramble.text;

import java.util.EnumMap;
import java.util.Map;
import org.slf4j.Logger;
import org.spongepowered.configurate.ConfigurationNode;

/**
 * Reads a loaded messages.yml node into a {@link MessageStore}.
 *
 * <p>Missing keys fall back to their built-in default and are reported once, so
 * an update that adds a message does not need the administrator to rewrite the
 * file. As with the other configs, the file itself is never written back.
 */
public final class MessageConfig {
    public static final String FILE_NAME = "messages.yml";
    public static final String DEFAULT_PREFIX = "<green>[Unscramble]</green> ";
    /** Where the retired config.yml claim-message now lives. */
    public static final String CLAIM_HINT_PATH = MessageKey.WIN_CLAIM_HINT.path();
    private static final String PREFIX_PATH = "prefix";

    private MessageConfig() {
    }

    public static MessageStore from(ConfigurationNode root, Logger logger) {
        return from(root, logger, null);
    }

    /**
     * @param legacyClaimMessage the retired claim-message from config.yml, or
     *     null. When given it overrides win.claim-hint, so a server upgrading
     *     from a version without messages.yml keeps the wording it had. The
     *     caller decides when that applies; see ConfigManager.
     */
    public static MessageStore from(ConfigurationNode root, Logger logger,
                                    String legacyClaimMessage) {
        Map<MessageKey, String> values = new EnumMap<>(MessageKey.class);
        int missing = 0;

        for (MessageKey key : MessageKey.values()) {
            ConfigurationNode node = root.node(key.nodePath());
            String value = node.getString();
            if (value == null) {
                values.put(key, key.defaultValue());
                missing++;
            } else {
                values.put(key, value);
            }
        }

        String prefix = root.node(PREFIX_PATH).getString();
        if (prefix == null) {
            prefix = DEFAULT_PREFIX;
            missing++;
        }

        if (legacyClaimMessage != null && !legacyClaimMessage.isEmpty()) {
            // Carried over so an upgrading server keeps the wording it had.
            values.put(MessageKey.WIN_CLAIM_HINT, "<prefix>" + legacyClaimMessage);
            logger.warn("Carried the retired 'claim-message' from config.yml over to '{}'. "
                    + "Set it in {} instead and delete the old key.",
                    CLAIM_HINT_PATH, FILE_NAME);
        }

        if (missing > 0) {
            logger.warn("{} is missing {} message(s); the built-in text is used for those. "
                            + "Compare it with the messages.yml inside the jar to see what is new.",
                    FILE_NAME, missing);
        }

        return new MessageStore(values, prefix);
    }
}
