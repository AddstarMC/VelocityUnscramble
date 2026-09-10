package au.com.addstar.velocityunscramble.text;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts ampersand/section colour codes to MiniMessage tags, and strips them
 * from text that should carry no formatting.
 */
public final class ColourCodes {
    private static final Pattern CODE = Pattern.compile("(?i)[&§]([0-9A-FK-OR])");

    private static final Map<Character, String> TAGS = Map.ofEntries(
            Map.entry('0', "black"), Map.entry('1', "dark_blue"),
            Map.entry('2', "dark_green"), Map.entry('3', "dark_aqua"),
            Map.entry('4', "dark_red"), Map.entry('5', "dark_purple"),
            Map.entry('6', "gold"), Map.entry('7', "gray"),
            Map.entry('8', "dark_gray"), Map.entry('9', "blue"),
            Map.entry('a', "green"), Map.entry('b', "aqua"),
            Map.entry('c', "red"), Map.entry('d', "light_purple"),
            Map.entry('e', "yellow"), Map.entry('f', "white"),
            Map.entry('k', "obfuscated"), Map.entry('l', "bold"),
            Map.entry('m', "strikethrough"), Map.entry('n', "underlined"),
            Map.entry('o', "italic"), Map.entry('r', "reset"));

    private ColourCodes() {
    }

    /** Rewrites &-codes as MiniMessage tags. Input without codes is returned as-is. */
    public static String toMiniMessage(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        Matcher m = CODE.matcher(input);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            char code = Character.toLowerCase(m.group(1).charAt(0));
            String tag = TAGS.get(code);
            m.appendReplacement(out, tag == null ? "" : Matcher.quoteReplacement("<" + tag + ">"));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** Strips colour codes entirely - used to clean up incoming chat guesses. */
    public static String strip(String input) {
        return input == null ? "" : CODE.matcher(input).replaceAll("");
    }
}
