package au.com.addstar.velocityunscramble.config;

import java.util.ArrayList;
import java.util.List;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Setting;

@ConfigSerializable
public class MainConfig {
    @Setting("debug")
    public boolean debugEnabled = false;

    @Setting("random-words")
    public List<String> words = new ArrayList<>();

    @Setting("display-answer-on-failed-games")
    public boolean displayAnswer = true;

    @Setting("auto-game-enabled")
    public boolean autoGameEnabled = false;

    /**
     * Retired in favour of win.claim-hint in messages.yml. Still read so an
     * existing config.yml keeps working: when it is present and messages.yml
     * does not override win.claim-hint, this wins and a warning points at the
     * new home. Remove it from config.yml once the message has been moved.
     */
    @Setting("claim-message")
    public String claimMessage = null;

    @Setting("points-table")
    public List<PointsTableEntry> pointsTable = new ArrayList<>();

    @Setting("db-url")
    public String dbURL = "jdbc:mysql://localhost:3306/db_name";

    @Setting("db-username")
    public String dbUsername = "user";

    @Setting("db-password")
    public String dbPassword = "pass";

    @ConfigSerializable
    public static class PointsTableEntry {
        @Setting("difficulty")
        public int difficulty;

        @Setting("points")
        public int points;

        public PointsTableEntry() {
        }

        public PointsTableEntry(int difficulty, int points) {
            this.difficulty = difficulty;
            this.points = points;
        }
    }
}
