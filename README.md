<div align="center">
  <h1>💎 Amethyst 🛡️</h1>
  <p>A prediction-based anti-cheat for PowerNukkitX</p>
  <p><a href="README_FR.md">🇫🇷 Lire en français</a></p>

  <p><i>Movement prediction - the server replaying a player's movement and correcting what it cannot explain.</i></p>
</div>

https://github.com/user-attachments/assets/3234cf7e-ab7a-4094-abf4-8962ffd592f8

<div align="center">
  <p><i>Vehicle prediction - a boat sent back with its rider still aboard.</i></p>
</div>

https://github.com/user-attachments/assets/3801e2b6-ccc6-463d-b7f6-ccfa25b209c6

<div align="center">
  <p><sub>Both recordings were made on a private test server to show the checks reacting. Nothing here endorses
  or advertises cheating, and no cheat client is linked or distributed.</sub></p>
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

A prediction engine is picked for the medium the player is in - ground and air, water, lava, gliding - and it
carries a server-owned motion state forward through the real Bedrock tick order. The constants are the game's
own, and the trigonometry goes through a reimplementation of Mojang's sine table, so the float error matches
the client rather than merely being close to it.

It is authoritative, not observational: the inbound packet is rewritten with the simulated position before the
server sees it.

### Measuring one tick, not a lifetime

The simulation restarts from the position the client reports at the end of every verified tick. Without that,
a single unexplained tick stays in the server's position for hundreds of ticks, and **every one of them is
counted again as a fresh failure**. The offset means *how far this tick's move missed*, not *how far the server
has drifted since login*.

### Why a buffer, and not a threshold

A single threshold catches nothing: a cheat that stays under it, or that only offends every other tick, never
trips it. Each tick's unexplained excess is accumulated instead, and decays on every tick that matches. Flight
and speed are small and *persistent*, which is exactly what a buffer sees and a threshold does not.

### Trying more than one explanation

The client tells us a jump or a sprint started, but the flag and the tick it applies to do not always line up,
and a server impulse is applied when the packet *arrives*, not when the server armed it. An ambiguous tick is
therefore simulated several ways and the branch landing closest to the client wins. A cheat gains nothing,
because every branch is still a legal move - the residual it has to explain is unchanged.

### Seeing the world the client sees

State is compared against the world **as the client knew it**, not as the server currently holds it. A block
change stays pending until the client acknowledges it; a change the player made themselves applies at once,
because the client predicts its own placements and breaks; combat rewinds targets to their interpolated
client-side positions rather than raycasting against where the server thinks they are now.

Every state mutation the server sends - abilities, attributes, effects, armour, knockback, block updates - is
acknowledgement-gated, so the simulation only adopts a change once the client has actually seen it.

## 🔎 Checks

| Check | What it means |
| --- | --- |
| `Simulation` | Movement the physics simulation could not explain. Feeds the buffer; drives setbacks. |
| `Velocity-A` | A melee knockback the player did not travel, or travelled far beyond. The missing part is given back by moving them. |
| `Timer` | More client frames than ticks elapsed, which is a client running its own simulation fast. |
| `Vehicle-A` | Boat, minecart or mount movement that did not match its own prediction. The vehicle is sent back, not its rider. |
| `NoFall-A` | Fall damage that did not match the simulated fall. |
| `GroundSpoof-A` | The client claimed a vertical collision with nothing under it. |
| `Sprint-A…C` | A sprint state the client cannot legitimately hold. |
| `Elytra-A…B` | A glide started in conditions the client refuses. |
| `KillAura-A` | Invalid attack target or attack sequence. |
| `Autoclicker-A` | Clicks over the last second beyond the configured ceiling. The hit is refused. |
| `Reach-A` | Target attacked beyond the allowed reach, measured against its rewound hitbox. |
| `Hitbox-A` | The sight ray never intersected the target. |
| `BreakReach-A` | Block broken beyond the allowed distance. |
| `PlaceReach-A` | Block placed beyond that same distance. |
| `FastBreak-A` | Block destroyed before its server-calculated break time. |
| `Scaffold-A` | Zero click vector on an initial player-input placement. |
| `Cobweb-A` | Movement through a cobweb faster than its own slowdown allows. |
| `BadSlot-A` | A potion or ender pearl used from a slot outside the hotbar. The transaction is refused. |
| `FastUse-A` | A consumable finished in fewer ticks than any food or potion takes. |
| `InvMove-A` | Directed movement during an inventory interaction. |
| `BedrockTool-A` | The client identity matches a known tool. |
| `BadPacket-A…Q` | Malformed or impossible packet fields: values, states and identifiers the protocol cannot produce. A block placed without looking at it is refused here too. |

Invalid packets are cancelled. Repeated movement violations cause a setback to the last verified ground
position, and a player who has not reached one yet is only alerted on. `Timer` past a sustained run of
violations, `BedrockTool-A` on sight, and two of the `BadPacket` variants kick; nothing else does, and Amethyst
never bans.

Situations the game itself makes unpredictable - pistons, riptide, a player pushed inside a block, the moment
after a teleport - suspend the movement check rather than guess at it. They are handled conservatively so that
a legitimate player is never punished for them.

## 🔌 For developers

`PlayerViolationEvent` fires on every flag, before the alert is sent. It carries the player, the check, the
violation level and the same detail string the alert shows. Cancelling it suppresses the alert, which is how
another plugin exempts a case Amethyst cannot know about.

## ⚙️ Configuration

| Setting | Purpose |
| --- | --- |
| `alerts` | Enables violation alerts. |
| `dev-logs` | Adds diagnostic values to every alert. Off by default; needed to report a false positive. |
| `disabled-checks` | Check ids to turn off entirely, written as they appear in the alerts. |
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
| `combat.cps-limit` | Clicks per second accepted from a keyboard or controller. |
| `combat.touch-cps-limit` | Clicks per second accepted from touch input. |
| `blocks.max-reach` | Maximum accepted block interaction distance. |
| `blocks.break-leniency-ms` | Network leniency on the server-calculated mining time. |
| `inventory-move.input-threshold` | Minimum directional input, excluding controller drift. |
| `inventory-move.request-window-ms` | Maximum delay between an item action and movement confirmation. |
| `inventory-move.buffer-threshold` | Consecutive suspicious inventory actions required. |

`prediction.tolerance` and `prediction.buffer-threshold` are the two worth tuning.

## 🧭 Commands and permissions

`/amethyst status` reports the number of tracked players and the alert state, `/amethyst reload` reloads
`config.yml`, and `/amethyst alerts` toggles alerts globally until the next reload.

| Permission | Default | Description |
| --- | --- | --- |
| `amethyst.alerts` | OP | Receives alerts and allows `/amethyst`. |
| `amethyst.bypass` | Nobody | Skips every check. |

## 🐛 Reporting a false positive

Set `dev-logs: true` first - without it an alert names only the check, which is unreportable. The detailed line
carries the measured offset, both positions and the state the simulation was in.

Please open an issue with the unmodified line, what you were doing, the client version and input mode, the
approximate ping, and anything unusual nearby - vehicle, effects, equipment, blocks. **Say what you were
doing**: it is worth more than ten log lines without it.

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
