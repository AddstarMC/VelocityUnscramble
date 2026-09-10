package au.com.addstar.velocityunscramble.game;

import au.com.addstar.velocityunscramble.text.ColourCodes;
import au.com.addstar.velocityunscramble.text.MessageKey;
import au.com.addstar.velocityunscramble.text.Messages;
import au.com.addstar.velocityunscramble.text.MessageStore;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.scheduler.ScheduledTask;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** A single running game. One exists at a time, network-wide. */
public class Session implements Runnable {
    private static final List<String> BANNED_SCRAMBLES = List.of("shit", "craps", "parts", "piss");
    private static final int MAX_SHUFFLE_ATTEMPTS = 1000;

    private final GameManager manager;
    private final Random random;

    private final String word;
    private String scrambled;
    private boolean valid;

    private final long startTime;
    private final long endTime;
    private long lastAnnounce;

    private String hint;
    private final long hintInterval;
    private final int hintChars;
    private long lastHint;

    private int points;
    private int difficulty;

    private ScheduledTask task;
    private int chatLines;

    /** Guards the win path so two simultaneous correct guesses cannot both award. */
    private final AtomicBoolean finished = new AtomicBoolean();

    Session(GameManager manager, Random random, String word, long duration,
            long hintInterval, int hintChars) {
        this.manager = manager;
        this.random = random;
        this.word = word;
        this.startTime = System.currentTimeMillis();
        this.endTime = startTime + duration;
        this.hint = word.replaceAll("[^ ]", "*");
        this.hintInterval = hintInterval;
        this.hintChars = hintChars;
        scramble();
    }

    public boolean isValid() {
        return valid;
    }

    public String word() {
        return word;
    }

    public int points() {
        return points;
    }

    public int difficulty() {
        return difficulty;
    }

    void start() {
        difficulty = WordDifficulty.of(word);
        points = WordDifficulty.pointsFor(difficulty, manager.config().main().pointsTable);
        manager.debug("Difficulty: " + word + " = " + difficulty + ", points = " + points);

        // The word always gets its own line: it keeps the monospace font from
        // being crowded by the sentence, and long words no longer wrap awkwardly.
        manager.broadcast(MessageKey.GAME_START, "word", scrambled);
        manager.broadcast(MessageKey.GAME_START_WORD, "word", scrambled);

        manager.broadcast(MessageKey.GAME_PRIZE,
                "points", points,
                "plural", points == 1 ? "" : "s");

        task = manager.scheduler().buildTask(manager.plugin(), this)
                .delay(0, TimeUnit.SECONDS)
                .repeat(1, TimeUnit.SECONDS)
                .schedule();
        lastHint = System.currentTimeMillis();
    }

    /** Cancels the game without a winner. */
    public void stop() {
        if (!finished.compareAndSet(false, true)) {
            return;
        }
        cancelTask();
        manager.onSessionFinish();
        manager.broadcast(MessageKey.GAME_CANCELLED);
        if (manager.config().main().displayAnswer) {
            manager.broadcast(MessageKey.GAME_ANSWER_WAS, "word", word);
        }
    }

    private void cancelTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /**
     * Tests a chat message or /guess against the word. Must stay cheap - this
     * runs on the chat path, which gates forwarding for the whole network.
     */
    public void makeGuess(Player player, String rawGuess) {
        if (finished.get()) {
            return;
        }

        String guess = ColourCodes.strip(rawGuess);
        if (guess.startsWith("!")) {
            guess = guess.substring(1);
        }

        if (!word.equalsIgnoreCase(guess)) {
            if (++chatLines > 10) {
                chatLines = 0;
                manager.broadcast(MessageKey.GAME_REMINDER, "word", scrambled);
            }
            return;
        }

        // Reject shouted answers, matching the original behaviour.
        if (guess.matches(".*[A-Z ]{10,200}.*")) {
            manager.scheduler().buildTask(manager.plugin(),
                            () -> manager.send(player, MessageKey.WIN_TOO_MANY_CAPS))
                    .delay(500, TimeUnit.MILLISECONDS)
                    .schedule();
            return;
        }

        if (!finished.compareAndSet(false, true)) {
            return;
        }

        double duration = Math.round((getTimeRunning() / 1000d) * 100.0) / 100.0;
        cancelTask();
        manager.onSessionFinish();
        manager.scheduler().buildTask(manager.plugin(),
                        () -> manager.awardWin(player, this, duration))
                .delay(200, TimeUnit.MILLISECONDS)
                .schedule();
    }

    @Override
    public void run() {
        long left = getTimeLeft();
        if (left <= 0) {
            if (!finished.compareAndSet(false, true)) {
                return;
            }
            cancelTask();
            manager.onSessionFinish();
            manager.broadcast(MessageKey.GAME_TIMED_OUT);
            if (manager.config().main().displayAnswer) {
                manager.broadcast(MessageKey.GAME_ANSWER_WAS, "word", word);
            }
            return;
        }

        long sinceLastAnnounce = System.currentTimeMillis() - lastAnnounce;
        boolean announce = (sinceLastAnnounce >= 1000 && left <= 3000)
                || (sinceLastAnnounce >= 10000 && left <= 30000)
                || (sinceLastAnnounce >= 15000 && left <= 60000)
                || sinceLastAnnounce >= 30000;

        if (announce) {
            lastAnnounce = System.currentTimeMillis();
            manager.broadcast(MessageKey.GAME_TIME_LEFT, "time", getTimeLeftString());
        }

        if (hintInterval != 0 && System.currentTimeMillis() - lastHint >= hintInterval) {
            doHint();
            lastHint = System.currentTimeMillis();
        }
    }

    public void doHint() {
        if (countChar(hint, '*') <= hintChars) {
            return;
        }

        int charsRevealed = 0;
        while (true) {
            int index = random.nextInt(word.length());
            char c = word.charAt(index);
            if (c != ' ' && hint.charAt(index) == '*') {
                char[] chars = hint.toCharArray();
                chars[index] = c;
                hint = new String(chars);
                if (++charsRevealed >= hintChars) {
                    break;
                }
            }
        }

        manager.broadcast(MessageKey.GAME_HINT, "hint", hint);
    }

    private long getTimeRunning() {
        return System.currentTimeMillis() - startTime;
    }

    private long getTimeLeft() {
        return endTime - System.currentTimeMillis();
    }

    /**
     * Builds the countdown text from the configurable time.* fragments. The
     * result is inserted into game.time-left as a placeholder value, so it is
     * assembled as plain text: any formatting belongs on the surrounding
     * message, not on the pieces.
     */
    private String getTimeLeftString() {
        MessageStore messages = manager.messages();
        long time = (long) Math.ceil(getTimeLeft() / 1000D) * 1000;
        StringBuilder buffer = new StringBuilder();

        long minutes = TimeUnit.MILLISECONDS.toMinutes(time);
        if (minutes > 0) {
            buffer.append(minutes == 1
                    ? Messages.plain(messages.get(MessageKey.TIME_MINUTE))
                    : Messages.plain(messages.get(MessageKey.TIME_MINUTES, "count", minutes)));
            time -= TimeUnit.MINUTES.toMillis(minutes);
        }

        long seconds = TimeUnit.MILLISECONDS.toSeconds(time);
        if (seconds > 0) {
            if (!buffer.isEmpty()) {
                buffer.append(" ");
            }
            buffer.append(seconds == 1
                    ? Messages.plain(messages.get(MessageKey.TIME_SECOND))
                    : Messages.plain(messages.get(MessageKey.TIME_SECONDS, "count", seconds)));
        }

        return Messages.plain(
                messages.get(MessageKey.TIME_SUFFIX, "time", buffer.toString()));
    }

    private void scramble() {
        String[] words = word.split(" ");
        boolean ok = true;

        for (int i = 0; i < words.length; ++i) {
            String current = words[i];
            if (current.length() <= 1) {
                continue;
            }

            List<Character> chars = new ArrayList<>(current.length());
            for (int c = 0; c < current.length(); ++c) {
                chars.add(current.charAt(c));
            }

            int attempts = 0;
            while ((current.equals(words[i]) || BANNED_SCRAMBLES.contains(current))
                    && attempts < MAX_SHUFFLE_ATTEMPTS) {
                attempts++;
                Collections.shuffle(chars, random);
                StringBuilder builder = new StringBuilder(current.length());
                chars.forEach(builder::append);
                current = builder.toString();
            }

            if (attempts >= MAX_SHUFFLE_ATTEMPTS) {
                manager.logger().warn("Unable to find a valid shuffle after {} attempts. "
                                + "Phrase: \"{}\", word: \"{}\", last attempt: \"{}\"",
                        MAX_SHUFFLE_ATTEMPTS, word, words[i], current);
                ok = false;
            }

            words[i] = current;
        }

        scrambled = String.join(" ", words);
        valid = ok;
    }

    private static int countChar(String str, char c) {
        int count = 0;
        for (int i = 0; i < str.length(); i++) {
            if (str.charAt(i) == c) {
                count++;
            }
        }
        return count;
    }
}
