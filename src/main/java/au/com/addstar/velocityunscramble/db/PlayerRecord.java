package au.com.addstar.velocityunscramble.db;

import java.util.UUID;

/** A player's unscramble totals. */
public record PlayerRecord(UUID playerId, int totalPoints, int points, int wins) {
    public static PlayerRecord empty(UUID id) {
        return new PlayerRecord(id, 0, 0, 0);
    }
}
