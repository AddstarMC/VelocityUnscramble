package au.com.addstar.velocityunscramble.game;

import au.com.addstar.velocityunscramble.config.MainConfig;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Scrabble-value based difficulty scoring. Pure logic, no plugin state.
 */
public final class WordDifficulty {
    private static final Map<Character, Integer> SCRABBLE_VALUES = new HashMap<>();

    static {
        for (char c : "aeilnorstu".toCharArray()) {
            SCRABBLE_VALUES.put(c, 1);
        }
        for (char c : "dg".toCharArray()) {
            SCRABBLE_VALUES.put(c, 2);
        }
        for (char c : "bcmp".toCharArray()) {
            SCRABBLE_VALUES.put(c, 3);
        }
        for (char c : "fhvwy".toCharArray()) {
            SCRABBLE_VALUES.put(c, 4);
        }
        SCRABBLE_VALUES.put('k', 5);
        for (char c : "jx".toCharArray()) {
            SCRABBLE_VALUES.put(c, 8);
        }
        for (char c : "qz".toCharArray()) {
            SCRABBLE_VALUES.put(c, 10);
        }
    }

    /** Common English words score 30% of face value. */
    private static final Set<String> COMMON_WORDS = new HashSet<>(Arrays.asList(
            "a", "an", "the", "is", "are", "was", "were", "and", "or", "but",
            "if", "then", "that", "this", "it", "of", "on", "in", "at", "to",
            "with", "for", "from", "by", "about", "as", "into", "like", "through",
            "after", "over", "between", "out", "up", "down", "all", "no", "not",
            "some", "more", "most", "few", "fewer", "many", "much", "any", "every",
            "other", "such", "only", "just", "also", "very", "really", "even", "well",
            "now", "then", "there", "here", "how", "where", "when", "why", "what", "which"));

    /** Common Minecraft words score 70% of face value. */
    private static final Set<String> MINECRAFT_WORDS = new HashSet<>(Arrays.asList(
            "block", "pickaxe", "sword", "wool", "axe", "shovel", "stone", "dirt", "grass", "diamond",
            "gold", "iron", "coal", "wood", "plank", "log", "torch", "bed", "chest", "door", "craft",
            "table", "armor", "helmet", "boots", "zombie", "creeper", "skeleton", "spider", "enderman",
            "villager", "emerald", "fish", "bow", "arrow", "food", "water", "lava", "bucket", "boat",
            "rail", "minecart", "redstone", "lever", "button", "piston", "portal", "nether", "end",
            "bee", "sheep", "cow", "pig", "horse", "cat", "dog", "fox", "panda", "sugar", "cane",
            "cake", "cookie", "apple", "carrot", "potato", "pumpkin", "melon", "seeds", "wheat",
            "bread", "map", "compass", "clock", "shield", "banner", "sign", "book", "ink", "string",
            "leather", "bone", "slime", "clay", "sand", "gravel", "glass", "ice", "snow", "brick",
            "carpet", "bedrock", "sandstone", "eye", "ender"));

    private WordDifficulty() {
    }

    public static int of(String phrase) {
        double adjustedScore = 0;
        for (String word : phrase.split("\\s+")) {
            String lower = word.toLowerCase();
            double multiplier = 1.0;
            if (COMMON_WORDS.contains(lower)) {
                multiplier = 0.3;
            } else if (MINECRAFT_WORDS.contains(lower)) {
                multiplier = 0.7;
            }
            for (char c : lower.toCharArray()) {
                Integer value = SCRABBLE_VALUES.get(c);
                if (value != null) {
                    adjustedScore += (int) (value * multiplier);
                }
            }
        }
        return (int) Math.round(adjustedScore);
    }

    /**
     * Walks the configured table and returns the points for the highest
     * threshold not exceeding the given difficulty.
     */
    public static int pointsFor(int difficulty, List<MainConfig.PointsTableEntry> table) {
        int points = 1;
        for (MainConfig.PointsTableEntry entry : table) {
            if (entry.difficulty > difficulty) {
                break;
            }
            points = entry.points;
        }
        return points;
    }
}
