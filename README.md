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

`MovementSimulator` picks the engine that matches the medium the player is in - ground and air, water, lava,
gliding - and hands it the whole tick. Each engine carries a server-owned motion state forward through the
real Bedrock tick order for that medium: on the ground that is friction from the block underfoot, input
acceleration, jump, climbables, cobwebs, collision, then gravity; in water it is fluid drag, depth strider and
a gravity a sixteenth of its usual pull. The constants are the game's own - `0.91` air friction, `0.98` gravity
multiplier, `0.5625` step height - and the trigonometry goes through a reimplementation of Mojang's
65536-entry sine table, so the float error matches the client rather than merely being close to it.

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
| `Velocity-A` | A melee knockback the player did not travel, or travelled far beyond. The missing part is given back by moving them. |
| `Timer` | More client frames than ticks elapsed, which is a client running its own simulation fast. |
| `Vehicle-A` | Boat, minecart or mount movement that did not match its own prediction. The vehicle is sent back, not its rider. |
| `NoFall-A` | Fall damage that did not match the simulated fall. |
| `GroundSpoof-A` | The client claimed a vertical collision with nothing under it. Skipped over partial blocks and while wearing an elytra. |
| `Sprint-A…C` | A sprint the client cannot legitimately hold: too little food, an item in use, or started while blinded. |
| `Elytra-A…B` | A glide started while riding, or restarted within two ticks of the last one. |
| `KillAura-A` | Invalid attack target or attack sequence. |
| `Reach-A` | Target attacked beyond the allowed reach, measured against its rewound hitbox. |
| `Hitbox-A` | The sight ray never intersected the target. Players only, since a mob's rewound box is smoothed too far to raycast against. |
| `BreakReach-A` | Block broken beyond the allowed distance. |
| `PlaceReach-A` | Block placed beyond that same distance. |
| `FastBreak-A` | Block destroyed before its server-calculated break time. |
| `WeirdPlace-A` | A block placed against something the player was not looking at, or while not holding it. The placement is refused. |
| `Scaffold-A` | Zero click vector on an initial player-input placement. |
| `Cobweb-A` | Movement through a cobweb faster than its own slowdown allows. |
| `BadSlot-A` | A potion or ender pearl used from a slot outside the hotbar. The transaction is refused. |
| `FastUse-A` | A consumable finished in fewer ticks than any food or potion takes. |
| `InvMove-A` | Directed movement during an inventory interaction. |
| `BedrockTool-A` | The client identity matches a known tool: fixed device model, empty geometry version and a blank skin. |
| `BadPacket-A…J` | Malformed or impossible packet fields: non-finite values, stale ticks, invalid slots, faces, channels and enums. |

Invalid packets are cancelled. Repeated movement violations cause a setback to the last verified ground
position - never while in a fluid - and a player who has not reached one yet is only alerted on. `Timer`
past fifteen violations,
`BedrockTool-A` on sight, and `BadPacket-D` and `BadPacket-E` kick; nothing else does, and Amethyst never bans.

## 🔌 For developers

`PlayerViolationEvent` fires on every flag, before the alert is sent. It carries the player, the check, the
violation level and the same detail string the alert shows. Cancelling it suppresses the alert, which is how
another plugin exempts a case Amethyst cannot know about.

## ⚙️ Configuration

| Setting | Purpose |
| --- | --- |
| `alerts` | Enables violation alerts. |
| `dev-logs` | Adds the offset, both positions and the simulation state to every alert. Off by default; needed to report a false positive. |
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
| Swimming | The swim pose is where the fluid model drifts furthest, so it is skipped - but only while water is actually there, so claiming to swim in mid-air buys nothing. |
| The second after levitation ends | The client resumes falling on its own while the server still holds the effect's vertical velocity. |
| Bamboo, scaffolding | Client-side behaviour the simulation does not reproduce. |
| Pistons | A player being pushed is displaced by the server, not by their input. |
| Riptide | A ~3 block/tick burst the simulation cannot produce. |
| Inside a solid block | How the client pushes out of a block placed on it is its own affair. |
| Within 1.5 blocks of a boat or minecart | It floats and moves on its own, and its tracked position is a tick behind. |
| Teleports, respawns, joins | The state is rebuilt at the destination, with two to three seconds of grace. |
| A movement speed above 0.5 | The player crosses more ground in a tick than the captured snapshot holds, so the simulation would be walking through blocks it never saw. |

Water, lava and elytra flight are simulated rather than skipped, each by its own engine, but none of
them triggers a setback and their tolerance is eight times the usual one. Gliding keeps that
treatment for a second after it ends, since a landing carries the flight's velocity into the first
ticks on the ground. Below that speed cap, the tolerance also
scales with whatever movement speed the server granted, so a speed potion widens the margin by
exactly as much as it widens the step.

Boats, minecarts and shulkers are real collisions rather than holes: their type is carried through the entity
tracker so a player stands on them the way the client does.

Waterwalk is still caught despite the swimming exemption, because the cheat keeps the player *above* the
surface, where no fluid intersects their box and the ground engine runs as usual.

## 🚧 Not finished

- **The water model is young.** It was written against a working implementation and its constants are the
  game's own, but it went live in a single sitting. Swimming is skipped for that reason; wading is not.
- **Stair corners are derived, not read.** The server models every stair as straight, so the shape is
  recomputed from the neighbours the way the game does it. That code has not met a real staircase yet.
- **`Cobweb-A`, `BadSlot-A`, `Sprint-A…C`, `Elytra-A…B`, `GroundSpoof-A`, `FastUse-A`,
  `WeirdPlace-A`, `PlaceReach-A` and `BedrockTool-A` are new** and have not been through a
  false-positive pass. The `Sprint` and `Elytra` families were derived from a Java-edition anti-cheat
  and only `Sprint-A` is confirmed against the server's own source, so the rest may not describe
  Bedrock at all.
- **No tests.** The physics is exactly the kind of code a test suite would pin down, and there is none.
- **No replay harness.** Every diagnosis today needs a server restart and a human reproducing the bug. Recording
  inputs and world frames to disk, and replaying them offline, would turn a diagnosis cycle from minutes into
  seconds and give every fixed bug a regression case.
- **Two collision solvers** coexist - one for vehicles, one for the player.
- **No performance measurement.** The hot path is understood and has been reduced, but never profiled.

## 🐛 Reporting a false positive

Set `dev-logs: true` first - without it an alert names only the check, which is unreportable. The detailed
line carries the offset, both positions, the ground flag, what the simulation believed was supporting the
player, its vertical velocity, and the tick:

```
failed Simulation (VL 1.0) offset=0.420 client=[…] predicted=[…] ground=true
  support=minecraft:sand@61 below=minecraft:sand@60/1 vy=-0.078 kb-age=4 tick=334
```

`support` is what the simulation believed was holding the player up, `below` is what the captured frame holds
under them and how many collision boxes it kept for it, and `kb-age` is how long ago an impulse reached the
simulation. In a fluid the line also carries `sub`, how far the surface stands above the player's feet, and
the word `fluid` when the tick ran through a fluid engine.

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
