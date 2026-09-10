package au.com.addstar.velocityunscramble.text;

import java.util.EnumMap;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/**
 * The loaded contents of messages.yml, resolved to components on demand.
 *
 * <p>Values are MiniMessage. Legacy {@code &} colour codes are converted first,
 * so configs carried over from the Bungee plugin keep working. Every message may
 * use {@code <prefix>}; the rest of the tags a message understands are listed on
 * its {@link MessageKey}.
 *
 * <p>An instance is immutable once built. Reloading makes a new one, so a game
 * in flight can never see a half-loaded set of messages.
 */
public final class MessageStore {
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final Map<MessageKey, String> values;
    private final String prefix;

    public MessageStore(Map<MessageKey, String> values, String prefix) {
        this.values = new EnumMap<>(values);
        this.prefix = prefix == null ? "" : prefix;
    }

    /** A store with nothing overridden, used before the config loads. */
    public static MessageStore defaults() {
        Map<MessageKey, String> values = new EnumMap<>(MessageKey.class);
        for (MessageKey key : MessageKey.values()) {
            values.put(key, key.defaultValue());
        }
        return new MessageStore(values, MessageConfig.DEFAULT_PREFIX);
    }

    /** The raw configured string for a key, before parsing. */
    public String raw(MessageKey key) {
        String value = values.get(key);
        return value == null ? key.defaultValue() : value;
    }

    /** The configured prefix, as raw MiniMessage. */
    public String rawPrefix() {
        return prefix;
    }

    /**
     * Renders a message. Placeholder arguments are name/value pairs, and values
     * are inserted literally - a player name or a guessed word can never inject
     * MiniMessage tags of its own.
     */
    public Component get(MessageKey key, Object... placeholders) {
        return render(raw(key), placeholders);
    }

    /**
     * Renders arbitrary configured text (currently the auto-game prefix and any
     * value composed elsewhere) with the same rules as a keyed message.
     */
    public Component render(String value, Object... placeholders) {
        if (placeholders.length % 2 != 0) {
            throw new IllegalArgumentException(
                    "Placeholders must be name/value pairs, got " + placeholders.length);
        }

        TagResolver[] resolvers = new TagResolver[(placeholders.length / 2) + 1];
        resolvers[0] = Placeholder.parsed("prefix", ColourCodes.toMiniMessage(prefix));
        for (int i = 0; i < placeholders.length; i += 2) {
            resolvers[(i / 2) + 1] = Placeholder.unparsed(
                    String.valueOf(placeholders[i]), String.valueOf(placeholders[i + 1]));
        }

        return MM.deserialize(ColourCodes.toMiniMessage(value), resolvers);
    }

    /** True when a message is configured to render nothing, so it can be skipped. */
    public boolean isBlank(MessageKey key) {
        return raw(key).isBlank();
    }
}
