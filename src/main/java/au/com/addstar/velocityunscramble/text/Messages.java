package au.com.addstar.velocityunscramble.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * Helpers for text that is not itself a configurable message.
 *
 * <p>Everything a player sees comes from {@link MessageStore} and messages.yml.
 */
public final class Messages {
    private Messages() {
    }

    public static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
