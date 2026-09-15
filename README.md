# Slayer Clog Check

A RuneLite sidebar plugin that shows your **collection log progress for your current slayer task**, so
you can decide at a glance whether to keep or skip it - and compares every option offered by
**Mortimer** before you commit to one.

## Features

- **Current task**: lists the collection log items obtainable on your task, marked ✓ obtained or
  ✗ missing, with a keep-or-skip hint.
- **Broken down per monster**: each killable version of the task gets its own section with its own
  count: the base monster, the **Superior** variant (imbued heart, eternal gem, dust/mist battlestaff),
  and any **boss** version (e.g. Gargoyles → Grotesque Guardians, Araxytes → Araxxor).
- **Drops logged elsewhere**: task monsters whose drops land on other collection log pages are included
  too, e.g. demonic gorillas (Glough's Experiments), the Royal Titans on fire/ice giant tasks, God Wars
  bodyguards, and champion scrolls.
- **Mortimer comparison**: when Mortimer offers you a choice of tasks, the panel shows what each
  option could add to your log, side by side.
- **Konar & Wilderness tasks**: a Konar task also shows its assigned **location**, plus the
  **Brimstone chest** rewards (mystic dusk set, broken dragon hasta) that only Konar tasks can yield;
  Krystilia's wilderness tasks show the **Larran's chest** rewards (Dagon'hai robes).
- **Live updates**: a newly obtained item ticks immediately from the in-game collection log message;
  opening a collection log page also syncs that page.

## How obtained status is determined

RuneLite cannot read most collection log items from varbits. This plugin learns your progress from two
sources:

1. Opening a collection log page reads that page's items directly.
2. The "New item added to your collection log" message marks that item immediately.

Progress is stored per account under `.runelite/slayer-clog-check/`. Items whose status is not yet known
show `?` until you open the relevant collection log page once.

The task-to-item mapping is bundled with the plugin, so it works entirely offline: no external
requests are made at runtime.

## Requirements

The built-in **Slayer** plugin must be enabled; this plugin reads your current task from it.

## Building

Requires JDK 11 or newer.

```
./gradlew build
```

## License

BSD 2-Clause. See [LICENSE](LICENSE).
