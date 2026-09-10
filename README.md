# VelocityUnscramble

Word unscramble game for the AddstarMC Velocity proxy. A scrambled word is
broadcast network-wide; the first player to type the answer wins points.

This replaces the BungeeCord plugin `BungeeUnscramble`, which ran on Velocity
through the Snap compatibility shim. This version is a native Velocity plugin
and does not need Snap.

## Requirements

- Velocity 4.1.x
- Java 25
- MySQL

There is **no backend component**. Rewards are points only, awarded on the proxy
and stored in MySQL, so nothing needs installing on game servers.

## Commands

| Command | Permission | Description |
|---|---|---|
| `/unscramble` (alias `/us`) | none | Shows help |
| `/us help` | none | Shows help |
| `/us stats` / `/us points` | none | Your wins, current points and total points earned |
| `/guess <word>` | none | Submit an answer explicitly, instead of typing in chat |
| `/us reload` | `unscramble.reload` | Reloads `config.yml`, `auto.yml` and `messages.yml` |
| `/us hint` | `unscramble.hint` | Reveals more characters of the current word |
| `/us cancel` | `unscramble.cancel` | Cancels the running game |
| `/us debug` | `unscramble.debug` | Toggles debug logging |
| `/us newgame [word] [time] [hint-interval] [hint-chars]` | `unscramble.newgame` | Starts a game |

Guesses are normally typed straight into chat. `/guess` exists for cases where a
chat plugin consumes the message, and as a fallback if reading proxy chat ever
stops being viable.

`/us claim` still responds, but only to explain that prizes are now awarded
automatically as points. It will be removed in a later release.

### `newgame` arguments

Every argument is optional and they are positional:

```
/us newgame                          # random word, default timings
/us newgame hello                    # chosen word
/us newgame hello_world 60 12 2      # word, time, hint interval, hint chars
```

Omitting the word picks one at random from `random-words`. `time` and
`hint-interval` are in seconds and default to 30 and 12; `hint-chars` defaults
to 2. Underscores in the word become spaces, so a phrase fits in one argument.
Set `hint-interval` to `0` to disable hints for that game.

## Permissions

| Node | Grants |
|---|---|
| `unscramble.reload` | `/us reload` |
| `unscramble.hint` | `/us hint` |
| `unscramble.cancel` | `/us cancel` |
| `unscramble.newgame` | `/us newgame` |
| `unscramble.debug` | `/us debug` |

Help, stats and guessing need no permission. Subcommands the sender lacks
permission for are hidden from tab completion.

## Configuration

Three files, all written on first run and never rewritten afterwards, so
comments and edits are safe. `/us reload` re-reads all three.

### `config.yml`

| Key | Default | Meaning |
|---|---|---|
| `debug` | `false` | Verbose logging |
| `random-words` | `[]` | The word pool every game draws from |
| `display-answer-on-failed-games` | `true` | Reveal the answer when nobody wins |
| `auto-game-enabled` | `false` | Run games automatically on a timer |
| `points-table` | `[]` | Difficulty thresholds mapped to point awards |
| `db-url` | `jdbc:mysql://localhost:3306/db_name` | JDBC URL |
| `db-username` | `user` | Database user |
| `db-password` | `pass` | Database password |

`points-table` is a list of `difficulty`/`points` pairs. The highest threshold
not exceeding a word's difficulty wins:

```yaml
points-table:
  - difficulty: 0
    points: 1
  - difficulty: 23
    points: 3
  - difficulty: 33
    points: 5
```

### `messages.yml`

Every message a player can see. Change any of them freely; the plugin never
rewrites the file, so edits and comments survive restarts and reloads. When an
update adds a message, the plugin logs how many are missing and uses the
built-in text for those until they are added.

Values are MiniMessage, so colours, gradients, hover, click and custom fonts
all work:

```yaml
prefix: "<gradient:#00ff88:#00aaff>[Unscramble]</gradient> "

game:
  hint: "<prefix><yellow>Hint!... <gold><font:addstarmc:mono><hint>"

win:
  broadcast: "<prefix><hover:show_text:'Solved <word> in <duration>s'><yellow>Congratulations <aqua><player></aqua>!"
```

Legacy `&` colour codes still work, so wording carried over from
BungeeUnscramble keeps rendering.

#### The scrambled word and the monospace font

A game announces on two lines: `game.start` carries the sentence and
`game.start-word` carries the word on its own.

```
[Unscramble] New Game! Unscramble this:
> romsuhom lsdani
```

The word, the hints, the reminder and the revealed answer are rendered in
`<font:addstarmc:mono>`, the monospace font from the AddstarMC resource pack.
Every letter is the same width in it, so the word cannot be read from letter
spacing and the hint asterisks line up underneath. Players without the pack
fall back to the default font and see proportional text.

To put the word back on the announcement line, move `<word>` into `game.start`
and set `game.start-word` to `""`.

Placeholders are written as tags such as `<word>` or `<player>`. Each message
documents the ones it understands, and they are all optional; drop one and it
will not appear. `<prefix>` works in every message and expands to the `prefix`
set at the top of the file, so removing it from a line renders that line
without a prefix. Placeholder values are inserted as literal text, so a player
name or a guessed word cannot inject formatting of its own.

Setting a message to `""` sends nothing. That is how you switch off an
individual announcement.

Message groups:

| Group | Covers |
|---|---|
| `prefix` | Prepended wherever `<prefix>` appears |
| `game.*` | Game start, prize, countdown, hints, timeout, cancellation, the revealed answer |
| `time.*` | The words used to build the countdown (`1 Minute`, `<count> Seconds`) |
| `win.*` | The win broadcast, the winner's point total, the rewards and stats hints |
| `stats.*` | `/us stats` output |
| `command.*` | Command feedback and errors |
| `help.*` | The `/unscramble help` screen |

`time.*` entries are assembled into the `<time>` placeholder as plain text, so
put formatting on `game.time-left` around them rather than on the pieces.

### `auto.yml`

| Key | Default | Meaning |
|---|---|---|
| `interval` | `15` | Minutes between automatic games |
| `random-offset` | `5` | Randomly skews `interval` by up to this many minutes each way |
| `length` | `30` | Game length in seconds |
| `warning-period` | `3` | Seconds of warning before an automatic game starts |
| `hint-interval` | `12` | Seconds between hints (`0` disables) |
| `hint-chars` | `2` | Characters revealed per hint |
| `min-players` | `3` | Automatic games are skipped below this player count |

## Scoring

Difficulty is the sum of Scrabble letter values. Common English words score 30%
of face value and common Minecraft words 70%, so obscure words are worth more.
The resulting difficulty is looked up in `points-table` to decide the award.

## Database

Two tables, created automatically if missing:

- `players`: `playerid` (UUID), `totalpoints`, `points`, `wins`
- `wins`: one row per win, with `playerid`, `phrase`, `duration`, `difficulty`, `points`

A win updates both in a single transaction. All database access is asynchronous;
nothing blocks the chat or command threads.

## Migrating from BungeeUnscramble

The config format has changed, so write `config.yml` and `auto.yml` fresh rather
than copying the old files across. The keys are listed above; the word list and
database settings are the ones worth carrying over.

- `points-table` is now a list of `difficulty`/`points` objects rather than
  `"difficulty:points"` strings.
- `auto.yml` holds only timings. Any `prizes` block is ignored, as item and
  money rewards are retired, and words come from `random-words` in `config.yml`.
- `claim-message` has moved to `win.claim-hint` in `messages.yml`. A
  `claim-message` still in `config.yml` is carried over automatically on the run
  that creates `messages.yml`, with a warning; after that `messages.yml` decides
  and the old key is ignored. Delete it once the text has been moved.

`unclaimed.yml` is no longer used. Settle any outstanding prizes before
switching, as the new plugin cannot award them.

The database schema is unchanged, so `players` and `wins` carry over as they are.

Remove `BungeeUnscramble.jar`, and remove Snap once no other plugin needs it.
The old `unscramble.ms` CommandHelper script on the backends is now dead and can
be unbound.

## Building

```
./gradlew build
```

The shaded jar is written to `build/libs/`. HikariCP is relocated; the MySQL
driver is bundled unrelocated so JDBC service discovery keeps working.
