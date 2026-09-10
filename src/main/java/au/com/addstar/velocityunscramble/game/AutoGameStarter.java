package au.com.addstar.velocityunscramble.game;

import au.com.addstar.velocityunscramble.text.MessageKey;
import java.util.concurrent.TimeUnit;

/**
 * Fires an auto game after a warning broadcast, then reschedules itself.
 * Skips (and retries shortly) if too few players are online.
 */
public class AutoGameStarter implements Runnable {
    private final GameManager manager;
    private final int warningPeriod;
    private final int minPlayers;
    private boolean warned;

    AutoGameStarter(GameManager manager, int warningPeriod, int minPlayers) {
        this.manager = manager;
        this.warningPeriod = warningPeriod;
        this.minPlayers = minPlayers;
    }

    @Override
    public void run() {
        if (!warned) {
            if (manager.proxy().getPlayerCount() < minPlayers) {
                manager.debug("Not enough players online for an auto game; rescheduling.");
                manager.scheduleNextGame();
                return;
            }
            if (manager.isSessionRunning()) {
                manager.debug("A game is already running; rescheduling.");
                manager.scheduleNextGame();
                return;
            }

            warned = true;
            manager.broadcast(MessageKey.GAME_STARTING_SOON, "seconds", warningPeriod);

            manager.scheduler().buildTask(manager.plugin(), this)
                    .delay(warningPeriod, TimeUnit.SECONDS)
                    .schedule();
            return;
        }

        manager.startAutoGame();
        manager.scheduleNextGame();
    }
}
