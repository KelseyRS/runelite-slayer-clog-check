# Slayer Clog Check

A RuneLite sidebar plugin that shows your **collection log progress for your current slayer task**, so
you can decide at a glance whether to keep or skip it. Mostly designed for Mortimer Task Clog checking but is 
usable with other masters. 

## Features

- **Current task**: lists the collection log items obtainable on your task, marked ✓ obtained or
  ✗ missing, with a keep-or-skip hint.
- **Mortimer comparison**: when Mortimer offers you a choice of tasks, the panel shows what each
  option could add to your log, side by side.
- **Drop rates**: optionally put each item's drop rate under its name, for example (1:512)
- **Hide superior drops**: optionally hide the Superior section from the tables
- **Live updates**: a newly obtained item ticks immediately from the in-game collection log message;
  opening a collection log page also syncs that page.

## Kill counts and superiors

- **Kill counts** come from the game's own logs and are remembered per account. Open the Slayer Kill Log
  (enchanted gem or slayer ring, "Log") and the Boss Kill Log (ring of wealth, "Boss Log") once; boss counts
  are also read from collection log pages and each "Your X kill count is" message. The highest number seen
  is kept.
- **Superiors across all tasks** is the "Superior creatures" total from the Slayer Kill Log. Open the log to
  update it.
- **Superiors per task** is counted by the plugin itself, because the game does not record it: each "A
  superior foe has appeared..." message adds one to your current task. It starts counting from version
  1.3, and counts superiors that appeared, not kills.

## How obtained status is determined

RuneLite cannot read most collection log items from data. This plugin updates  progress from two
sources:
1. Opening a collection log page reads that page's items directly.
2. The "New item added to your collection log" message marks that item immediately.

Progress is stored per account under `.runelite/slayer-clog-check/`. Items whose status is not yet known
show `?` until you open the relevant collection log page once.


## Requirements

The built-in **Slayer** plugin must be enabled; this plugin reads your current task from it.

## Building

Requires JDK 11 or newer.

```
./gradlew build
```

## License

BSD 2-Clause. See [LICENSE](LICENSE).
