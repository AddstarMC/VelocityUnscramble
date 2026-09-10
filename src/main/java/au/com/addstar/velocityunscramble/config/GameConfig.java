package au.com.addstar.velocityunscramble.config;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Setting;

/** auto.yml - timings for automatically started games. */
@ConfigSerializable
public class GameConfig {
    @Setting("interval")
    public int interval = 15;

    @Setting("random-offset")
    public int randomOffset = 5;

    @Setting("length")
    public int length = 30;

    @Setting("warning-period")
    public int warningPeriod = 3;

    @Setting("hint-interval")
    public int hintInterval = 12;

    @Setting("hint-chars")
    public int hintChars = 2;

    @Setting("min-players")
    public int minPlayers = 3;
}
