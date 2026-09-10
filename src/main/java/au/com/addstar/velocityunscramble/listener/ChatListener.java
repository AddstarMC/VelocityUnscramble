package au.com.addstar.velocityunscramble.listener;

import au.com.addstar.velocityunscramble.game.GameManager;
import au.com.addstar.velocityunscramble.game.Session;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerChatEvent;

/**
 * Watches chat for the answer.
 *
 * <p>Strictly read-only. PlayerChatEvent is an awaiting event: Velocity holds
 * the chat packet until every handler returns, so anything slow here delays
 * chat for the whole network. It also must never call setResult() - denying or
 * modifying a signed message disconnects the player on 1.19.1+.
 */
public class ChatListener {
    private final GameManager manager;

    public ChatListener(GameManager manager) {
        this.manager = manager;
    }

    @Subscribe
    public void onPlayerChat(PlayerChatEvent event) {
        Session session = manager.currentSession();
        if (session != null) {
            session.makeGuess(event.getPlayer(), event.getMessage());
        }
    }
}
