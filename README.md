<div align="center">
  <h1>💎 Amethyst 🛡️</h1>
  <p>A prediction-based anti-cheat for PowerNukkitX</p>
  <p><a href="README_FR.md">🇫🇷 Lire en français</a></p>
</div>

## 📖 What this is

Most anti-cheats for Bedrock compare a player's movement against a threshold: move more than *n* blocks in a
tick and you are flagged. That catches nothing subtle and punishes anyone with a bad connection.

Amethyst replays the player's movement instead. Every tick, it runs the input the client sent through a
reimplementation of Bedrock's physics and compares the result against the position the client reports. What is
measured is not *how fast did you move*, but **how much of your movement the game's own rules cannot explain**.

## 📥 Installation

Drop the jar into your server's `plugins/` directory. No dependencies.

Requires PowerNukkitX and Java 21.

## 🧠 How it works

### The simulation

`MovementSimulator` carries a server-owned motion state forward through the real Bedrock tick order: friction
from the block underfoot, input acceleration, jump, climbables, cobwebs, collision, then gravity. The constants
are the game's own - `0.91` air friction, `0.98` gravity multiplier, `0.5625` step height - and the trigonometry
goes through a reimplementation of Mojang's 65536-entry sine table, so the float error matches the client
rather than merely being close to it.

It is authoritative, not observational: the inbound packet is rewritten with the simulated position before the
server sees it.

### Measuring one tick, not a lifetime

The simulation restarts from the position the client reports at the end of every verified tick. Without that,
a single unexplained tick - a lost packet, an impulse that arrived late - stays in the server's position for
hundreds of ticks, and **every one of them is counted again as a fresh failure**. The offset has to mean *how
far this tick's move missed*, not *how far the server has drifted since login*.

The consequence is that the server's own position is never more than a tick away from wherever the player
claims to be, so it cannot serve as the target of a correction. A separate last-verified position, only
recorded while standing on the ground with an empty violation buffer, is what a setback aims at.

### Why a buffer, and not a threshold

A single 0.5-block threshold catches nothing: a cheat that stays under it, or that only offends every other
tick, never trips it. Each tick's unexplained excess is accumulated instead, and decays on every tick that
matches. Flight and speed are small and *persistent*, which is exactly what a buffer sees and a threshold
does not.

### Trying more than one explanation

The client tells us a jump or a sprint started, but the flag and the tick it applies to do not always line up,
and a server impulse is applied when the packet *arrives*, not when the server armed it. Simulating only the
literal reading of the input makes the server miss a real 0.42 jump, which then looks like a large offset on a
completely legitimate move.

So an ambiguous tick is simulated both ways and the branch landing closest to the client wins; an impulse that
the client has not spent yet stays armed for the next tick. A cheat gains nothing, because every branch is
still a legal move - the residual it has to explain is unchanged.

### Seeing the world the client sees

State is compared against the world **as the client knew it**, not as the server currently holds it:

- A block change stays pending until the client acknowledges it, and `resolve()` returns the old state until
  then - the single largest source of false positives on Bedrock.
- A change the player made themselves is applied immediately, because the client predicts its own placements
  and breaks without waiting for anyone.
- Combat rewinds targets to their interpolated client-side positions, reproducing the client's own
  interpolation window, rather than raycasting against where the server thinks they are now.

Every state mutation the server sends - abilities, attributes, effects, armour, knockback, block updates - is
gated behind a `NetworkStackLatencyPacket` acknowledgement, so the simulation only adopts a change once the
client has actually seen it.

## 🔎 Checks

| Check | What it means |
| --- | --- |
| `Simulation` | Movement the physics simulation could not explain. Feeds the buffer; drives setbacks. |
| `Vehicle-A` | Boat, minecart or mount movement that did not match its own prediction. |
| `NoFall-A` | Fall damage that did not match the simulated fall. |
| `KillAura-A` | Invalid attack target or attack sequence. |
| `Reach-A` | Target attacked beyond the allowed reach, measured against its rewound hitbox. |
| `Hitbox-A` | The sight ray never intersected the target. |
| `Break-Reach` | Block broken beyond the allowed distance. |
| `FastBreak-A` | Block destroyed before its server-calculated break time. |
| `Scaffold-A` | Zero click vector on an initial player-input placement. |
| `InvMove-A` | Directed movement during an inventory interaction. |
| `BadPacket-A…J` | Malformed or impossible packet fields: non-finite values, stale ticks, invalid slots, faces, channels and enums. |

Invalid packets are cancelled. Repeated movement violations cause a setback to the last verified position.
`BadPacket-D` and `BadPacket-E` kick; nothing else does, and Amethyst never bans.

## ⚙️ Configuration

| Setting | Purpose |
| --- | --- |
| `alerts` | Enables violation alerts. |
| `updates.check` | Checks for a newer release on startup. |
| `setback-violations` | Violations required before a movement setback. |
| `max-packet-actions` | Maximum block actions accepted in one input packet. |
| `prediction.tolerance` | Offset, in blocks, ignored on a single tick. |
| `prediction.buffer-threshold` | Accumulated excess offset needed before flagging. |
| `prediction.buffer-decay` | Removed from the buffer on every matching tick. |
| `vehicle.tolerance` | Accepted vehicle model error. |
| `vehicle.buffer-threshold` | Buffered vehicle error required for a violation. |
| `vehicle.buffer-decay` | Vehicle buffer removed after valid movement. |
| `combat.bbox-expansion` | Expansion applied to rewound target hitboxes. |
| `combat.reach-leniency` | Additional tolerance applied to the predicted ray only. |
| `combat.interpolation-steps` | Partial-tick samples used for attacker and target rewind. |
| `combat.maximum-attack-angle` | Maximum accepted angle toward the target hitbox. |
| `combat.close-range-fallback` | Short-range fallback accepted without a raycast. |
| `combat.close-range-angle` | Maximum angle allowed by that fallback. |
| `blocks.max-reach` | Maximum accepted block interaction distance. |
| `blocks.break-leniency-ms` | Network leniency on the server-calculated mining time. |
| `inventory-move.input-threshold` | Minimum directional input, excluding controller drift. |
| `inventory-move.request-window-ms` | Maximum delay between an item action and movement confirmation. |
| `inventory-move.buffer-threshold` | Consecutive suspicious inventory actions required. |

`prediction.tolerance` and `prediction.buffer-threshold` are the two worth tuning. Their defaults are a
starting point, not a measurement.

## 🧭 Commands and permissions

`/amethyst status` reports the number of tracked players and the alert state, `/amethyst reload` reloads
`config.yml`, and `/amethyst alerts` toggles alerts globally until the next reload.

| Permission | Default | Description |
| --- | --- | --- |
| `amethyst.alerts` | OP | Receives alerts and allows `/amethyst`. |
| `amethyst.bypass` | Nobody | Skips every check. |

## ⚠️ Where the simulation gives up

Some situations are not reproduced, and the check is suspended rather than guessed at. These are genuine holes,
and they are listed because a cheat that knows about them can use them:

| Situation | Why |
| --- | --- |
| Water | The swim model is written but unverified, and off behind `MovementOptions.simulateWater`. |
| Lava | Not modelled. |
| Bamboo, scaffolding | Client-side behaviour the simulation does not reproduce. |
| Pistons | A player being pushed is displaced by the server, not by their input. |
| Riptide | A ~3 block/tick burst the simulation cannot produce. |
| Inside a solid block | How the client pushes out of a block placed on it is its own affair. |
| Teleports, respawns, joins | The state is rebuilt at the destination, with two seconds of grace. |

Waterwalk is still caught despite the water exemption, because the cheat keeps the player *above* the surface,
where no fluid intersects their box.

## 🚧 Not finished

- **`Timer` and `Velocity-A`** are listed as checks and cannot currently fire. The tick-budget counter they
  need exists; nothing consumes it yet.
- **No tests.** The physics is exactly the kind of code a test suite would pin down, and there is none.
- **No replay harness.** Every diagnosis today needs a server restart and a human reproducing the bug. Recording
  inputs and world frames to disk, and replaying them offline, would turn a diagnosis cycle from minutes into
  seconds and give every fixed bug a regression case.
- **Two collision solvers** coexist - one for vehicles, one for the player.
- **No performance measurement.** The hot path is understood and has been reduced, but never profiled.

## 🐛 Reporting a false positive

The alert line is the important part. It carries the offset, both positions, the ground flag, what the
simulation believed was supporting the player, its vertical velocity, and the tick:

```
failed Simulation (VL 1.0) offset=0.420 client=[…] predicted=[…] ground=true support=minecraft:sand@61 vy=-0.078 tick=334
```

Read it before reporting it - the shape of the numbers usually names the bug:

| Signature | Usually means |
| --- | --- |
| A multiple of 0.216 | Whole ticks are missing (0.216 = one tick of walking, 0.28 sprinting) |
| The same value repeating exactly | An impulse applied on the wrong tick - and the value says which one |
| A steadily decreasing series | The simulation is not re-anchoring |
| Difference only in `y` | A fall, or a dispute about the ground |
| `client` equal to `predicted` | An old build |

Please include the unmodified line, what you were doing, the client version and input mode, the approximate
ping, and anything unusual nearby - vehicle, effects, equipment, blocks. **Say what you were doing**: "just the
spear" has been worth more than ten log lines without it.

Avoid widening a threshold to make a report go away. A false positive is a bug in the model, and the fix
belongs in the model.

## 🔨 Build

Place `powernukkitx.jar` in the parent directory, then:

```
./gradlew clean build
```

The plugin lands in `build/libs/`.

## Author

Nay
