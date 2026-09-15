# Slayer Clog Check

A RuneLite sidebar plugin that shows your **collection log progress for your current slayer task**, so
you can decide at a glance whether to keep or skip it. Mostly designed for Mortimer Task Clog checking but is 
usable with other masters. 

## Features

- **Current task**: lists the collection log items obtainable on your task, marked ✓ obtained or
  ✗ missing, with a keep-or-skip hint.
- **Mortimer comparison**: when Mortimer offers you a choice of tasks, the panel shows what each
  option could add to your log, side by side.
- **Live updates**: a newly obtained item ticks immediately from the in-game collection log message;
  opening a collection log page also syncs that page.

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
