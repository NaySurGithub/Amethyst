<div align="center">
  <img src="./icon.png" alt="Amethyst logo" width="160">
  <h1>Amethyst</h1>
  <p><b>A prediction-based anti-cheat for PowerNukkitX</b></p>

  <a href="https://github.com/NaySurGithub/Amethyst/releases/latest">
    <img alt="Release" src="https://img.shields.io/github/v/release/NaySurGithub/Amethyst?style=flat&label=release&logo=github">
  </a>&nbsp;&nbsp;
  <a href="https://github.com/NaySurGithub/Amethyst/actions/workflows/release.yml">
    <img alt="Build" src="https://img.shields.io/github/actions/workflow/status/NaySurGithub/Amethyst/release.yml?style=flat&label=build&logo=github">
  </a>
</div>

Amethyst is a free, open source anti-cheat for Minecraft: Bedrock Edition servers running
[PowerNukkitX](https://github.com/PowerNukkitX/PowerNukkitX). Instead of comparing movement against fixed
limits, it replays every tick through a reimplementation of Bedrock's physics and flags what the game's own
rules cannot explain. It runs inside the server as a plugin: no proxy, no extra hop, no second copy of the world.

## 📥 Download

Get the latest jar from **[GitHub Releases](https://github.com/NaySurGithub/Amethyst/releases/latest)**.

## ⚙️ Requirements & installation

- PowerNukkitX (API 3.0.0)
- Java 21 or higher

Drop the jar into `plugins/` and restart. There are no dependencies.

## ✨ Why Amethyst

### Movement simulation
- Every tick, the input the client sent runs through a Bedrock physics reimplementation: ground and air,
  water, lava, gliding, riptide, vehicles.
- Constants are the game's own, and trigonometry uses a copy of Mojang's sine table, so rounding matches the
  client instead of merely coming close.
- Block behaviour is modelled explicitly: ice, soul sand, honey, slime and bed bounces, ladders, cobwebs, sweet
  berry bushes, powder snow (including leather boots) and stairs.
- The simulation is authoritative: the inbound packet is rewritten with the simulated position before the
  server sees it.

### Accuracy over thresholds
- **One tick, not a lifetime.** The simulation restarts from the client's position after every verified tick,
  so one unexplained tick is counted once instead of for as long as the drift lasts.
- **A buffer, not a threshold.** Each tick's unexplained excess accumulates and decays on every matching tick.
  Fly and speed are small and persistent, which is exactly what a buffer catches and a threshold misses.
- **Several explanations tried.** When a jump or sprint flag and the tick it applies to do not line up, the tick
  is simulated each way and the closest legal branch wins. Every branch is a legal move, so a cheat gains nothing.

### Latency compensation
- Movement is compared against the world **as the client knew it**. Block changes stay pending until the client
  acknowledges them; a player's own placements and breaks apply at once, as the client predicts them.
- Abilities, attributes, effects, armour, knockback and block updates are acknowledgement-gated: the simulation
  adopts a change only once the client has seen it.
- Combat rewinds targets to their interpolated client-side positions before measuring reach and hitboxes.

### Container concealment
- An optional layer against X-ray and ESP: containers a player cannot see are simply not sent (see below).

## 🔎 Detections

**Movement**
| Check | Detects |
| --- | --- |
| `Simulation` | Movement the physics simulation cannot explain. Drives setbacks. |
| `Fly-A` | Hovering while the simulation requires a fall. |
| `NoFall-A` | Fall damage that does not match the simulated fall. |
| `Velocity-A` | Knockback not taken, or taken far beyond what was sent. |
| `Timer` | More client frames than elapsed ticks. |
| `Vehicle-A` | Boat, minecart or mount movement that does not match its prediction. |
| `Phase-A` | Travelling through full solid blocks. |
| `Cobweb-A` | Moving through a cobweb faster than it allows. |
| `Sprint-A…C` | Sprinting with too little food, while using an item, or while blinded. |
| `Elytra-A…B` | Gliding started while riding, or restarted too soon. |

**Combat**
| Check | Detects |
| --- | --- |
| `Reach-A` | Attacks beyond reach, measured against the rewound hitbox. |
| `Hitbox-A` | Attacks whose sight ray never touches the target. |
| `KillAura-A` | Invalid attack targets or sequences. |
| `Autoclicker-A` | Clicks per second above the configured ceiling. |
| `AutoTotem-A` | Totem swapped into the offhand within milliseconds of the last one popping. |
| `Backtrack-A` | Withheld entity positions replayed later. |

**World and inventory**
| Check | Detects |
| --- | --- |
| `BreakReach-A` / `PlaceReach-A` | Blocks broken or placed beyond reach. |
| `FastBreak-A` | Blocks destroyed before their server-calculated break time. |
| `Scaffold-A` | Placements with an impossible click vector. |
| `FastUse-A` | Consumables finished faster than any food or potion allows. |
| `BadSlot-A` | Potions or pearls used from outside the hotbar. |
| `InvMove-A` | Moving while interacting with an inventory. |
| `ChestStealer-A` | Items taken faster or more regularly than a human can click. |

**Protocol and client**
| Check | Detects |
| --- | --- |
| `BadPacket-A…P` | Malformed or impossible packet fields, including placing a block without looking at it. |
| `BedrockTool-A` | A client identity matching a known tool. |

## 🛡️ What happens on a flag

- Invalid packets are **cancelled**.
- Repeated movement violations cause a **setback** to the last verified ground position. A correction stays
  active until the client acknowledges it.
- A sustained `Timer` run, `BedrockTool-A` and two `BadPacket` variants **kick**. Nothing else does.
- Amethyst **never bans**. Punishment is left to you, through the developer API.

## 🫥 Container concealment

X-ray and ESP draw through terrain, and no packet betrays them. The only answer is to not send what the player
should not see. This feature is **off by default** (`conceal-containers.enabled`).

Every few ticks, each container in range is tested for line of sight from the player's eye. A container walled in
on every side is decided from its six neighbours alone; otherwise rays are traced to each exposed face turned
toward the player. A hidden container is sent as the block around it, or as air in the open, and comes back a
step before the sight line opens. Nearby containers are never touched, idle players are not rescanned, and
creative and spectator players are exempt.

## 🔧 Configuration

| Setting | Default | Purpose |
| --- | --- | --- |
| `alerts` | `true` | Violation alerts. |
| `dev-logs` | `false` | Adds offsets, positions and simulation state to alerts. Needed to report a false positive. |
| `disabled-checks` | `[]` | Check ids to turn off, as written in the alerts. |
| `updates.check` | `true` | Checks for a newer release on startup. |
| `setback-violations` | `2.0` | Violations before a movement setback. |
| `max-packet-actions` | `12` | Block actions accepted in one input packet. |
| `prediction.tolerance` | `0.05` | Offset ignored on a single tick, in blocks. |
| `prediction.buffer-threshold` | `0.6` | Accumulated excess needed before flagging. |
| `prediction.buffer-decay` | `0.08` | Removed from the buffer on every matching tick. |
| `vehicle.tolerance` | `0.16` | Accepted vehicle model error. |
| `vehicle.buffer-threshold` | `0.4` | Buffered vehicle error before a violation. |
| `vehicle.buffer-decay` | `0.1` | Removed after valid vehicle movement. |
| `timer.warmup-packets` | `40` | Input packets ignored after joining before `Timer` measures. |
| `timer.violation-samples` | `8` | Over-rate windows needed before a `Timer` violation, in eighths (`8` = one window). |
| `combat.bbox-expansion` | `0.1` | Expansion of rewound target hitboxes. |
| `combat.reach-leniency` | `0.0` | Extra tolerance on the predicted ray. |
| `combat.interpolation-steps` | `10` | Partial-tick samples for attacker and target rewind. |
| `combat.maximum-attack-angle` | `85.0` | Largest accepted angle toward the target. |
| `combat.close-range-fallback` | `1.75` | Distance under which no raycast is required. |
| `combat.close-range-angle` | `60.0` | Angle allowed by that fallback. |
| `combat.cps-limit` | `24` | Clicks per second from keyboard, mouse or controller. |
| `combat.touch-cps-limit` | `24` | Clicks per second from touch input. |
| `blocks.max-reach` | `7.0` | Block interaction distance. |
| `blocks.break-leniency-ms` | `75` | Network leniency on mining time. |
| `inventory-move.input-threshold` | `0.075` | Directional input ignored as controller drift. |
| `inventory-move.request-window-ms` | `750` | Window between an item action and movement. |
| `inventory-move.buffer-threshold` | `2` | Suspicious inventory actions in a row. |
| `conceal-containers.enabled` | `false` | Container concealment. |
| `conceal-containers.radius` | `48` | Range considered, in blocks. |
| `conceal-containers.disabled-containers` | `[minecraft:beacon]` | Containers never concealed. |
| `conceal-containers.min-distance` | `3` | Containers closer than this are never touched. |
| `conceal-containers.margin` | `0.5` | Side offset used to reveal a container before it comes into view. |
| `conceal-containers.interval` | `4` | Ticks between two passes. |

If you need to tune anything, start with `prediction.tolerance` and `prediction.buffer-threshold`.

## 🧭 Commands & permissions

| Command | Description |
| --- | --- |
| `/amethyst status` | Tracked players and alert state. |
| `/amethyst reload` | Reloads `config.yml`. |
| `/amethyst alerts` | Toggles alerts until the next reload. |

| Permission | Default | Description |
| --- | --- | --- |
| `amethyst.alerts` | OP | Receives alerts and allows `/amethyst`. |
| `amethyst.bypass` | Nobody | Skips every check. |

## ⚠️ Known limitations

- The movement check is **suspended rather than guessed** near moving blocks and pistons, bamboo and scaffolding,
  solid entities, and right after a teleport, as well as while flying or in no-clip.
- Vehicle movement is checked against a tolerance-based model, less precise than player movement.

## 🐛 Reporting a false positive

1. Set `dev-logs: true`. Without it, an alert only names the check, which cannot be investigated.
2. Open an [issue](https://github.com/NaySurGithub/Amethyst/issues) with:
   - the unmodified alert line;
   - **what you were doing** — worth more than ten log lines without it;
   - client version, input mode (keyboard, touch, controller) and approximate ping;
   - anything unusual nearby: vehicle, effects, equipment, blocks.

Please do not widen a threshold to make a false positive go away. It is a bug in the model, and the fix belongs
in the model.

## 🔌 Developer API

`PlayerViolationEvent` fires on every flag, before the alert is sent. It carries the player, the check, the
violation level and the alert's detail string. Cancelling it suppresses the alert, which is how another plugin
exempts a case Amethyst cannot know about, logs flags or applies its own punishments.

The physics lives in its own module, **`amethyst-simulation`**, which has no server dependency and can be tested
without a running server.

## 🔨 Building from source

```
git clone https://github.com/NaySurGithub/Amethyst.git
cd Amethyst
./gradlew build
```

The plugin jar is written to `build/libs/`.

## 👤 Author

Made by **Nay**.
