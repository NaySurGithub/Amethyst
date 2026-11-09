package nay.amethyst.simulation.movement;

public final class AuthoritativeMotionState {
    private final ClientMotionState client = new ClientMotionState();

    private FloatVector position = FloatVector.ZERO;
    private FloatVector lastPosition = FloatVector.ZERO;
    private FloatVector velocity = FloatVector.ZERO;
    private FloatVector lastVelocity = FloatVector.ZERO;
    private FloatVector movement = FloatVector.ZERO;
    private FloatVector lastMovement = FloatVector.ZERO;
    private FloatVector rotation = FloatVector.ZERO;
    private FloatVector lastRotation = FloatVector.ZERO;
    private FloatVector knockback = FloatVector.ZERO;

    private float slideOffsetX;
    private float slideOffsetY;
    private float impulseSideways;
    private float impulseForward;
    private float width = 0.6f;
    private float height = 1.8f;
    private float scale = 1.0f;
    private float gravity = MovementConstants.NORMAL_GRAVITY;
    private float jumpHeight = 0.42f;
    private float baseJumpHeight = 0.42f;
    private float fallDistance;
    private float movementSpeed = 0.1f;
    private float defaultMovementSpeed = 0.1f;
    private float airSpeed = 0.02f;
    /** Pending block slowdown multiplier, applied to next tick's move request (BDS block slowdown). */
    private float stuckX;
    private float stuckY;
    private float stuckZ;
    private int swiftSneakLevel;
    private boolean soulSpeed;
    private boolean weaving;
    private boolean inWater;
    private boolean nearFluid;
    private boolean headInWater;
    private float swimAmount;
    private int fallFlyTicks;
    private boolean instabuild;
    private boolean previousSneaking;
    private boolean pushTowardsClosestSpace;
    /** BDS collision recovery flags: 0x2/0x4 penetrated once/twice, 0x8 disabled, 0x10 push. */
    private int recoveryFlags;

    private static final int MAX_KNOCKBACK_DEFERRALS = 2;

    private long ticksSinceKnockback = Long.MAX_VALUE;
    private int knockbackDeferrals;
    private long ticksSinceTeleport = Long.MAX_VALUE;
    private int teleportCompletionTicks;
    private int pendingTeleports;
    private int pendingCorrections;
    private int jumpDelay;
    private int rawJumpGraceCooldown;
    private int glideBoostTicks;
    private int supportingBlockX;
    private int supportingBlockY;
    private int supportingBlockZ;
    private int riptideTicks;

    private FloatVector teleportPosition = FloatVector.ZERO;
    private FloatVector pendingTeleportPosition = FloatVector.ZERO;

    private boolean teleportSmoothed;
    private boolean sprinting;
    private boolean pressingSprint;
    private boolean swimming;
    private boolean stopSwimming;
    private boolean autoJumpingInWater;
    private int depthStrider;
    private boolean serverSprint;
    private boolean serverSprintApplied = true;
    private boolean serverUpdatedSpeed;
    private boolean sneaking;
    private boolean pressingSneak;
    private boolean jumping;
    private boolean pressingJump;
    private boolean collideX;
    private boolean collideY;
    private boolean collideZ;
    private boolean onGround;
    private boolean immobile;
    private boolean noClip;
    private boolean gliding;
    private boolean affectedByGravity = true;
    private boolean flying;
    private boolean mayFly;
    private boolean trustFlightStatus;
    private boolean justDisabledFlight;
    private boolean correctionCooldown;
    private boolean initialized;
    private boolean riptideActive;

    private int jumpBoostLevel;
    private int jumpBoostTicks;
    private int levitationLevel;
    private int levitationTicks;
    private boolean slowFalling;
    private int slowFallingTicks;
    private boolean alive = true;
    private boolean supportedGameMode = true;
    private boolean ready;
    private boolean consuming;
    private boolean wearingElytra;
    private boolean wearingLeatherBoots;
    private boolean legacySlideOffset;
    private boolean modernSprintTiming = true;
    private boolean hasSupportingBlock;
    private boolean descendingScaffold;

    public void updateInput(MovementInputFrame input) {
        client.update(input);
        updateFlight(input);

        lastRotation = rotation;
        rotation = input.rotation();
        pressingSneak = input.has(MovementInputFlag.SNEAKING);
        pressingSprint = input.has(MovementInputFlag.SPRINT_DOWN);
        stopSwimming = input.has(MovementInputFlag.STOP_SWIMMING);
        autoJumpingInWater = input.has(MovementInputFlag.AUTO_JUMPING_IN_WATER);
        if (input.has(MovementInputFlag.START_SWIMMING)) {
            swimming = true;
        } else if (stopSwimming) {
            swimming = false;
        }

        boolean startSprint = input.has(MovementInputFlag.START_SPRINTING);
        boolean stopSprint = input.has(MovementInputFlag.STOP_SPRINTING);
        boolean adjustSpeed = false;
        if (startSprint && stopSprint) {
            sprinting = false;
            airSpeed = MovementConstants.AIR_SPEED;
            adjustSpeed = modernSprintTiming;
        } else if (!startSprint && !stopSprint && !serverSprintApplied && serverSprint != sprinting) {
            sprinting = serverSprint;
            airSpeed = sprinting ? MovementConstants.SPRINT_AIR_SPEED : MovementConstants.AIR_SPEED;
        } else if (startSprint) {
            sprinting = true;
            airSpeed = MovementConstants.SPRINT_AIR_SPEED;
            adjustSpeed = modernSprintTiming;
        } else if (stopSprint) {
            sprinting = false;
            airSpeed = MovementConstants.AIR_SPEED;
            adjustSpeed = modernSprintTiming && !serverUpdatedSpeed;
        }
        serverSprintApplied = true;
        if (adjustSpeed) resetMovementSpeedForSprint();

        previousSneaking = sneaking;
        if (input.has(MovementInputFlag.START_SNEAKING)) {
            sneaking = true;
        } else if (input.has(MovementInputFlag.STOP_SNEAKING)) {
            sneaking = false;
        } else {
            sneaking = input.has(MovementInputFlag.SNEAK_DOWN);
        }

        // The client sends its processed move vector (normalised, sneak and item-use scaled).
        // Normalise past unit length, then cap each axis by the scale BDS would have applied:
        // honest input is left unchanged, as BDS does.
        float sideways = input.moveSideways();
        float forward = input.moveForward();
        float lengthSquared = forward * forward + sideways * sideways;
        if (lengthSquared > 1.0f) {
            float length = (float) Math.sqrt(lengthSquared);
            forward /= length;
            sideways /= length;
        }
        // the client scales by the sneak key or last tick's sneak flag, never while swimming nor in
        // water; the cap is skipped near any fluid so a disagreeing water test cannot over-cap
        float maximumInput = 1.0f;
        if ((pressingSneak || previousSneaking) && !swimming && !inWater && !nearFluid) {
            maximumInput *= sneakSpeed(swiftSneakLevel);
        }
        if (consuming) maximumInput *= MovementConstants.CONSUMING_INPUT;
        impulseSideways = MovementConstants.clamp(sideways, -maximumInput, maximumInput) * 0.98f;
        impulseForward = MovementConstants.clamp(forward, -maximumInput, maximumInput) * 0.98f;

        jumping = input.has(MovementInputFlag.START_JUMPING)
                || input.has(MovementInputFlag.JUMP_PRESSED_RAW);
        pressingJump = input.has(MovementInputFlag.JUMPING);
        // (effect + base): effect = (amplifier + 1) * 0.1, base = the jump strength attribute
        jumpHeight = (float) jumpBoostLevel * 0.1f + baseJumpHeight;
        if (!pressingJump) jumpDelay = 0;
        gravity = slowFalling ? MovementConstants.SLOW_FALLING_GRAVITY
                : MovementConstants.NORMAL_GRAVITY;

        if (input.has(MovementInputFlag.STOP_GLIDING)) {
            gliding = false;
            glideBoostTicks = 0;
        } else if (input.has(MovementInputFlag.START_GLIDING) && wearingElytra) {
            gliding = true;
        }
    }

    public void finishInput() {
        if (!modernSprintTiming) {
            serverUpdatedSpeed = false;
        }
        glideBoostTicks--;
        if (ticksSinceKnockback < Long.MAX_VALUE) {
            ticksSinceKnockback++;
        }
        if (ticksSinceTeleport < Long.MAX_VALUE) {
            ticksSinceTeleport++;
        }
        if (jumpDelay > 0) jumpDelay--;
        if (rawJumpGraceCooldown > 0) rawJumpGraceCooldown--;
        justDisabledFlight = false;
    }

    public void tickEffects() {
        jumpBoostTicks = tickEffect(jumpBoostTicks);
        if (jumpBoostTicks == 0) {
            jumpBoostLevel = 0;
        }
        levitationTicks = tickEffect(levitationTicks);
        if (levitationTicks == 0) {
            levitationLevel = 0;
        }
        slowFallingTicks = tickEffect(slowFallingTicks);
        if (slowFallingTicks == 0) {
            slowFalling = false;
        }
    }

    private static int tickEffect(int ticks) {
        if (ticks == Integer.MAX_VALUE) {
            return ticks;
        }
        return Math.max(0, ticks - 1);
    }

    private void updateFlight(MovementInputFrame input) {
        if (input.has(MovementInputFlag.START_FLYING)) {
            client.toggledFlight(true);
            if (trustFlightStatus) flying = true;
        } else if (input.has(MovementInputFlag.STOP_FLYING)) {
            if (flying) justDisabledFlight = true;
            flying = false;
            client.toggledFlight(false);
        }
    }

    private void resetMovementSpeedForSprint() {
        serverUpdatedSpeed = false;
        movementSpeed = defaultMovementSpeed;
        if (sprinting) movementSpeed *= 1.3f;
    }

    public void resetToClient() {
        lastPosition = client.lastPosition();
        position = client.position();
        lastVelocity = client.lastVelocity();
        velocity = client.velocity();
        lastMovement = client.lastMovement();
        movement = client.movement();
        if (flying) onGround = false;
        velocity = new FloatVector(
                MovementConstants.clamp(velocity.x(), -10.0f, 10.0f),
                MovementConstants.clamp(velocity.y(), -10.0f, 10.0f),
                MovementConstants.clamp(velocity.z(), -10.0f, 10.0f)
        );
    }

    public void initialize(FloatVector feetPosition, boolean onGround) {
        position = feetPosition;
        lastPosition = feetPosition;
        velocity = FloatVector.ZERO;
        lastVelocity = FloatVector.ZERO;
        movement = FloatVector.ZERO;
        lastMovement = FloatVector.ZERO;
        client.reset(feetPosition);
        this.onGround = onGround;
        ticksSinceKnockback = Long.MAX_VALUE;
        ticksSinceTeleport = Long.MAX_VALUE;
        initialized = true;
    }

    public void clearTransientMotion() {
        velocity = FloatVector.ZERO;
        lastVelocity = FloatVector.ZERO;
        movement = FloatVector.ZERO;
        lastMovement = FloatVector.ZERO;
        knockback = FloatVector.ZERO;
        ticksSinceKnockback = Long.MAX_VALUE;
        jumpDelay = 0;
        rawJumpGraceCooldown = 0;
        slideOffsetX = 0.0f;
        slideOffsetY = 0.0f;
        stopRiptide();
    }

    public void startRiptide() {
        riptideTicks = 0;
        riptideActive = true;
    }

    public void finishRiptideTick() {
        if (!riptideActive) return;
        riptideTicks++;
        if (collideX || collideZ || riptideTicks >= RiptidePhysics.MAX_SPIN_TICKS
                || (riptideTicks > RiptidePhysics.GROUND_STOP_DELAY_TICKS && onGround)) {
            stopRiptide();
        }
    }

    public void stopRiptide() {
        riptideTicks = 0;
        riptideActive = false;
    }

    public boolean riptideActive() {
        return riptideActive;
    }

    public FloatBox boundingBox() {
        float halfWidth = width * 0.5f * scale;
        float scaledHeight = height * scale;
        float yOffset = legacySlideOffset ? slideOffsetY : 0.0f;
        return new FloatBox(position.x() - halfWidth, position.y() + yOffset,
                position.z() - halfWidth, position.x() + halfWidth,
                position.y() + scaledHeight + yOffset, position.z() + halfWidth);
    }

    public FloatBox clientBoundingBox() {
        float halfWidth = width * 0.5f;
        float yOffset = legacySlideOffset ? slideOffsetY : 0.0f;
        FloatVector clientPosition = client.position();
        return new FloatBox(clientPosition.x() - halfWidth,
                clientPosition.y() + yOffset, clientPosition.z() - halfWidth,
                clientPosition.x() + halfWidth,
                clientPosition.y() + height + yOffset,
                clientPosition.z() + halfWidth);
    }

    public void teleport(FloatVector position, boolean onGround, boolean smoothed) {
        teleportPosition = position;
        teleportCompletionTicks = smoothed ? 2 : 0;
        teleportSmoothed = smoothed;
        this.onGround = onGround;
        ticksSinceTeleport = 0;
    }

    public boolean hasTeleport() {
        return ticksSinceTeleport <= teleportCompletionTicks;
    }

    public long ticksSinceKnockback() {
        return ticksSinceKnockback;
    }

    public boolean hasKnockback() {
        return ticksSinceKnockback == 0;
    }

    /** Holds an impulse back one tick, up to {@link #MAX_KNOCKBACK_DEFERRALS} times. */
    public boolean deferKnockback() {
        if (ticksSinceKnockback != 0 || knockbackDeferrals >= MAX_KNOCKBACK_DEFERRALS) {
            return false;
        }
        knockbackDeferrals++;
        ticksSinceKnockback = -1;
        return true;
    }

    public boolean claimRawJumpGrace(boolean rawJumpPressed) {
        if (!rawJumpPressed || !onGround || rawJumpGraceCooldown > 0) {
            return false;
        }
        rawJumpGraceCooldown = MovementConstants.JUMP_DELAY_TICKS;
        return true;
    }

    public void knockback(FloatVector knockback) {
        this.knockback = knockback;
        knockbackDeferrals = 0;
        ticksSinceKnockback = 0;
    }

    public ClientMotionState client() {
        return client;
    }

    public FloatVector position() {
        return position;
    }

    public void position(FloatVector value) {
        lastPosition = position;
        position = value;
    }

    public FloatVector lastPosition() {
        return lastPosition;
    }

    public FloatVector velocity() {
        return velocity;
    }

    public void velocity(FloatVector value) {
        lastVelocity = velocity;
        velocity = value;
    }

    public FloatVector lastVelocity() {
        return lastVelocity;
    }

    public FloatVector movement() {
        return movement;
    }

    public void movement(FloatVector value) {
        lastMovement = movement;
        movement = value;
    }

    public FloatVector lastMovement() {
        return lastMovement;
    }

    public FloatVector rotation() {
        return rotation;
    }

    public FloatVector lastRotation() {
        return lastRotation;
    }

    public FloatVector knockback() {
        return knockback;
    }

    public float impulseSideways() {
        return impulseSideways;
    }

    public float impulseForward() {
        return impulseForward;
    }

    public float slideOffsetX() {
        return slideOffsetX;
    }

    public float slideOffsetY() {
        return slideOffsetY;
    }

    public void slideOffset(float x, float y) {
        slideOffsetX = x;
        slideOffsetY = y;
    }

    public float width() {
        return width;
    }

    public float height() {
        return height;
    }

    public float scale() {
        return scale;
    }

    public void size(float width, float height, float scale) {
        this.width = width;
        this.height = height;
        this.scale = scale;
    }

    public float gravity() {
        return gravity;
    }

    /** Jump strength attribute (custom jump height), 0.42 by default. */
    public void baseJumpHeight(float value) {
        baseJumpHeight = value;
    }

    public float jumpHeight() {
        return jumpHeight;
    }

    public float fallDistance() {
        return fallDistance;
    }

    public void fallDistance(float value) {
        fallDistance = value;
    }

    public float movementSpeed() {
        return movementSpeed;
    }

    public void movementSpeed(float value) {
        movementSpeed = value;
        serverUpdatedSpeed = true;
    }

    public float defaultMovementSpeed() {
        return defaultMovementSpeed;
    }

    public void defaultMovementSpeed(float value) {
        defaultMovementSpeed = value;
    }

    public float airSpeed() {
        return airSpeed;
    }

    public int pendingTeleports() {
        return pendingTeleports;
    }

    public void addPendingTeleport(FloatVector position) {
        pendingTeleportPosition = position;
        pendingTeleports++;
    }

    public void removePendingTeleport() {
        pendingTeleports = Math.max(0, pendingTeleports - 1);
    }

    public void clearPendingTeleports() {
        pendingTeleports = 0;
    }

    public FloatVector pendingTeleportPosition() {
        return pendingTeleportPosition;
    }

    public FloatVector teleportPosition() {
        return teleportPosition;
    }

    public int teleportCompletionTicks() {
        return teleportCompletionTicks;
    }

    public void teleportCompletionTicks(int value) {
        teleportCompletionTicks = Math.max(0, value);
    }

    public boolean teleportSmoothed() {
        return teleportSmoothed;
    }

    public long ticksSinceTeleport() {
        return ticksSinceTeleport;
    }

    public boolean sprinting() {
        return sprinting;
    }

    public boolean swimming() {
        return swimming;
    }

    public boolean stopSwimming() {
        return stopSwimming;
    }

    public boolean autoJumpingInWater() {
        return autoJumpingInWater;
    }

    public int depthStrider() {
        return depthStrider;
    }

    public void depthStrider(int value) {
        depthStrider = value;
    }

    public boolean pressingSprint() {
        return pressingSprint;
    }

    public boolean sneaking() {
        return sneaking;
    }

    public boolean pressingSneak() {
        return pressingSneak;
    }

    public boolean jumping() {
        return jumping;
    }

    public void jumping(boolean value) {
        jumping = value;
    }

    public void sprinting(boolean value) {
        sprinting = value;
        airSpeed = value ? MovementConstants.SPRINT_AIR_SPEED : MovementConstants.AIR_SPEED;
        movementSpeed = value ? defaultMovementSpeed * 1.3f : defaultMovementSpeed;
    }

    public boolean pressingJump() {
        return pressingJump;
    }

    public int jumpDelay() {
        return jumpDelay;
    }

    public void jumpDelay(int value) {
        jumpDelay = Math.max(0, value);
    }

    public boolean collideX() {
        return collideX;
    }

    public boolean collideY() {
        return collideY;
    }

    public boolean collideZ() {
        return collideZ;
    }

    public void collisions(boolean x, boolean y, boolean z) {
        collideX = x;
        collideY = y;
        collideZ = z;
    }

    public boolean onGround() {
        return onGround;
    }

    public void onGround(boolean value) {
        onGround = value;
    }

    /** The last resolved move penetrated a shape (recovery flag 0x2 or 0x4). */
    public boolean penetratedLastFrame() {
        return (recoveryFlags & 6) != 0;
    }

    /** Push-out is suppressed: the move clips instead of recovering (BDS recovery limits of 0). */
    public boolean stuckInCollider() {
        return MovementCollisionEngine.recoveryLimit(recoveryFlags) == 0.0f;
    }

    public boolean immobile() {
        return immobile;
    }

    public void immobile(boolean value) {
        immobile = value;
    }

    public boolean noClip() {
        return noClip;
    }

    public void noClip(boolean value) {
        noClip = value;
    }

    public boolean gliding() {
        return gliding;
    }

    public void gliding(boolean value) {
        gliding = value;
    }

    public int glideBoostTicks() {
        return glideBoostTicks;
    }

    public void glideBoostTicks(int value) {
        glideBoostTicks = value;
    }

    public boolean affectedByGravity() {
        return affectedByGravity;
    }

    public void affectedByGravity(boolean value) {
        affectedByGravity = value;
    }

    public boolean flying() {
        return flying;
    }

    public void flying(boolean value) {
        flying = value;
    }

    public boolean mayFly() {
        return mayFly;
    }

    public void mayFly(boolean value) {
        mayFly = value;
    }

    public void trustFlightStatus(boolean value) {
        trustFlightStatus = value;
    }

    public boolean justDisabledFlight() {
        return justDisabledFlight;
    }

    public int pendingCorrections() {
        return pendingCorrections;
    }

    public void addPendingCorrection() {
        pendingCorrections++;
        correctionCooldown = true;
    }

    public void removePendingCorrection() {
        pendingCorrections = Math.max(0, pendingCorrections - 1);
    }

    public boolean correctionCooldown() {
        return correctionCooldown;
    }

    public void correctionCooldown(boolean value) {
        correctionCooldown = value;
    }

    public int jumpBoostLevel() {
        return jumpBoostLevel;
    }

    public void jumpBoostLevel(int value) {
        jumpBoostLevel = Math.max(0, value);
        jumpBoostTicks = jumpBoostLevel == 0 ? 0 : Integer.MAX_VALUE;
    }

    public void jumpBoost(int level, int duration) {
        jumpBoostLevel = Math.max(0, level);
        jumpBoostTicks = jumpBoostLevel == 0 ? 0 : effectDuration(duration);
    }

    public int levitationLevel() {
        return levitationLevel;
    }

    public void levitationLevel(int value) {
        levitationLevel = Math.max(0, value);
        levitationTicks = levitationLevel == 0 ? 0 : Integer.MAX_VALUE;
    }

    public void levitation(int level, int duration) {
        levitationLevel = Math.max(0, level);
        levitationTicks = levitationLevel == 0 ? 0 : effectDuration(duration);
    }

    public boolean slowFalling() {
        return slowFalling;
    }

    public void slowFalling(boolean value) {
        slowFalling = value;
        slowFallingTicks = value ? Integer.MAX_VALUE : 0;
    }

    public void slowFalling(boolean value, int duration) {
        slowFalling = value;
        slowFallingTicks = value ? effectDuration(duration) : 0;
    }

    private static int effectDuration(int duration) {
        return duration < 0 ? Integer.MAX_VALUE : duration;
    }

    public boolean alive() {
        return alive;
    }

    public void alive(boolean value) {
        alive = value;
    }

    public boolean supportedGameMode() {
        return supportedGameMode;
    }

    public void supportedGameMode(boolean value) {
        supportedGameMode = value;
    }

    public boolean ready() {
        return ready;
    }

    public void ready(boolean value) {
        ready = value;
    }

    public boolean consuming() {
        return consuming;
    }

    public void consuming(boolean value) {
        consuming = value;
    }

    public boolean wearingElytra() {
        return wearingElytra;
    }

    public void wearingElytra(boolean value) {
        wearingElytra = value;
    }

    public boolean wearingLeatherBoots() {
        return wearingLeatherBoots;
    }

    public void wearingLeatherBoots(boolean value) {
        wearingLeatherBoots = value;
    }

    public boolean legacySlideOffset() {
        return legacySlideOffset;
    }

    public void legacySlideOffset(boolean value) {
        legacySlideOffset = value;
    }

    public void modernSprintTiming(boolean value) {
        modernSprintTiming = value;
    }

    public boolean initialized() {
        return initialized;
    }

    public void serverSprint(boolean value) {
        serverSprint = value;
        serverSprintApplied = false;
    }

    public void abilities(boolean mayFly, boolean flying, boolean noClip) {
        this.mayFly = mayFly;
        this.flying = flying;
        this.noClip = noClip;
        if (client.toggledFlight()) {
            trustFlightStatus = flying || mayFly;
            client.toggledFlight(false);
        }
    }

    public boolean hasSupportingBlock() {
        return hasSupportingBlock;
    }

    /** Whether this tick sneaks down through scaffolding, which removes its top surface. */
    public boolean descendingScaffold() {
        return descendingScaffold;
    }

    public void descendingScaffold(boolean value) {
        descendingScaffold = value;
    }

    public void supportingBlock(int x, int y, int z) {
        supportingBlockX = x;
        supportingBlockY = y;
        supportingBlockZ = z;
        hasSupportingBlock = true;
    }

    public void clearSupportingBlock() {
        hasSupportingBlock = false;
    }

    public int supportingBlockX() {
        return supportingBlockX;
    }

    public int supportingBlockY() {
        return supportingBlockY;
    }

    public int supportingBlockZ() {
        return supportingBlockZ;
    }

    /** Swift Sneak speed: min(level * 0.15 + 0.3, 1), 0.3 without the enchantment. */
    public static float sneakSpeed(int swiftSneakLevel) {
        if (swiftSneakLevel <= 0) return MovementConstants.SNEAK_INPUT;
        return Math.min((float) swiftSneakLevel * 0.15f + 0.3f, 1.0f);
    }

    public int swiftSneakLevel() {
        return swiftSneakLevel;
    }

    public void swiftSneakLevel(int value) {
        swiftSneakLevel = Math.max(0, value);
    }

    /** Weaving effect: a first cobweb contact slows by (0.5, 0.25, 0.5) instead. */
    public boolean weaving() {
        return weaving;
    }

    public void weaving(boolean value) {
        weaving = value;
    }

    public boolean soulSpeed() {
        return soulSpeed;
    }

    public void soulSpeed(boolean value) {
        soulSpeed = value;
    }

    public boolean nearFluid() {
        return nearFluid;
    }

    public void nearFluid(boolean value) {
        nearFluid = value;
    }

    /** Eye cell water test (liquid height (y + 1) - (depth / 9 - 1 / 9)). */
    public boolean headInWater() {
        return headInWater;
    }

    public void headInWater(boolean value) {
        headInWater = value;
    }

    /** Swim pose blend, 0..1, +-0.1 per tick. */
    public float swimAmount() {
        return swimAmount;
    }

    public void swimAmount(float value) {
        swimAmount = value;
    }

    public int fallFlyTicks() {
        return fallFlyTicks;
    }

    public void fallFlyTicks(int value) {
        fallFlyTicks = value;
    }

    /** Creative instant build ability (enables the glide lift while jump is held). */
    public boolean instabuild() {
        return instabuild;
    }

    public void instabuild(boolean value) {
        instabuild = value;
    }

    public boolean pushTowardsClosestSpace() {
        return pushTowardsClosestSpace;
    }

    public void pushTowardsClosestSpace(boolean value) {
        pushTowardsClosestSpace = value;
    }

    public int recoveryFlags() {
        return recoveryFlags;
    }

    public void recoveryFlags(int value) {
        recoveryFlags = value;
    }

    /** Whether the last simulated tick was in water (input scaling skips sneak in water). */
    public boolean inWater() {
        return inWater;
    }

    public void inWater(boolean value) {
        inWater = value;
    }

    public float stuckX() {
        return stuckX;
    }

    public float stuckY() {
        return stuckY;
    }

    public float stuckZ() {
        return stuckZ;
    }

    public void stuckMultiplier(float x, float y, float z) {
        stuckX = x;
        stuckY = y;
        stuckZ = z;
    }

    public MotionSnapshot snapshot() {
        return new MotionSnapshot(this);
    }

    public void restore(MotionSnapshot snapshot) {
        snapshot.applyTo(this);
    }

    /** Everything a simulated tick may mutate, excluding teleport and knockback state. */
    public static final class MotionSnapshot {
        private final FloatVector position;
        private final FloatVector lastPosition;
        private final FloatVector velocity;
        private final FloatVector lastVelocity;
        private final FloatVector movement;
        private final FloatVector lastMovement;
        private final float slideOffsetX;
        private final float slideOffsetY;
        private final float movementSpeed;
        private final float airSpeed;
        private final int jumpDelay;
        private final int glideBoostTicks;
        private final int supportingBlockX;
        private final int supportingBlockY;
        private final int supportingBlockZ;
        private final int riptideTicks;
        private final long ticksSinceKnockback;
        private final int rawJumpGraceCooldown;
        private final boolean hasSupportingBlock;
        private final boolean onGround;
        private final boolean collideX;
        private final boolean collideY;
        private final boolean collideZ;
        private final boolean gliding;
        private final boolean sprinting;
        private final boolean jumping;
        private final boolean riptideActive;
        private final float stuckX;
        private final float stuckY;
        private final float stuckZ;
        private final boolean inWater;
        private final float swimAmount;
        private final int fallFlyTicks;
        private final int recoveryFlags;
        private final boolean headInWater;

        private MotionSnapshot(AuthoritativeMotionState state) {
            position = state.position;
            lastPosition = state.lastPosition;
            velocity = state.velocity;
            lastVelocity = state.lastVelocity;
            movement = state.movement;
            lastMovement = state.lastMovement;
            slideOffsetX = state.slideOffsetX;
            slideOffsetY = state.slideOffsetY;
            movementSpeed = state.movementSpeed;
            airSpeed = state.airSpeed;
            jumpDelay = state.jumpDelay;
            glideBoostTicks = state.glideBoostTicks;
            supportingBlockX = state.supportingBlockX;
            supportingBlockY = state.supportingBlockY;
            supportingBlockZ = state.supportingBlockZ;
            riptideTicks = state.riptideTicks;
            ticksSinceKnockback = state.ticksSinceKnockback;
            rawJumpGraceCooldown = state.rawJumpGraceCooldown;
            hasSupportingBlock = state.hasSupportingBlock;
            onGround = state.onGround;
            collideX = state.collideX;
            collideY = state.collideY;
            collideZ = state.collideZ;
            gliding = state.gliding;
            sprinting = state.sprinting;
            jumping = state.jumping;
            riptideActive = state.riptideActive;
            stuckX = state.stuckX;
            stuckY = state.stuckY;
            stuckZ = state.stuckZ;
            inWater = state.inWater;
            swimAmount = state.swimAmount;
            fallFlyTicks = state.fallFlyTicks;
            recoveryFlags = state.recoveryFlags;
            headInWater = state.headInWater;
        }

        public boolean onGround() {
            return onGround;
        }

        public boolean jumping() {
            return jumping;
        }

        public int jumpDelay() {
            return jumpDelay;
        }

        public boolean sprinting() {
            return sprinting;
        }

        private void applyTo(AuthoritativeMotionState state) {
            state.position = position;
            state.lastPosition = lastPosition;
            state.velocity = velocity;
            state.lastVelocity = lastVelocity;
            state.movement = movement;
            state.lastMovement = lastMovement;
            state.slideOffsetX = slideOffsetX;
            state.slideOffsetY = slideOffsetY;
            state.movementSpeed = movementSpeed;
            state.airSpeed = airSpeed;
            state.jumpDelay = jumpDelay;
            state.glideBoostTicks = glideBoostTicks;
            state.supportingBlockX = supportingBlockX;
            state.supportingBlockY = supportingBlockY;
            state.supportingBlockZ = supportingBlockZ;
            state.riptideTicks = riptideTicks;
            state.ticksSinceKnockback = ticksSinceKnockback;
            state.rawJumpGraceCooldown = rawJumpGraceCooldown;
            state.hasSupportingBlock = hasSupportingBlock;
            state.onGround = onGround;
            state.collideX = collideX;
            state.collideY = collideY;
            state.collideZ = collideZ;
            state.gliding = gliding;
            state.sprinting = sprinting;
            state.jumping = jumping;
            state.riptideActive = riptideActive;
            state.stuckX = stuckX;
            state.stuckY = stuckY;
            state.stuckZ = stuckZ;
            state.inWater = inWater;
            state.swimAmount = swimAmount;
            state.fallFlyTicks = fallFlyTicks;
            state.recoveryFlags = recoveryFlags;
            state.headInWater = headInWater;
        }
    }
}
