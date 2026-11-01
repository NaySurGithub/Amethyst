package nay.amethyst.simulation.movement;

/**
 * One tick of movement. Every medium runs the same Bedrock 1.26.52 / BDS travel order
 * ({@link #travel}); the subclasses only pick the medium.
 */
public abstract class PredictionEngine {
    private static final float SCAFFOLDING_SPEED = 0.15f;
    private static final float GRAVITY_EPSILON = Float.intBitsToFloat(0x34000000);
    private static final float WATER_GRAVITY = -0.005f;
    private static final float LAVA_GRAVITY = -0.02f;

    protected enum Medium { GROUND, WATER, LAVA, GLIDE }

    protected final AuthoritativeMotionState state;
    protected final MovementWorldView world;
    protected final MovementCollisionEngine collisions;
    protected final float correctionThreshold;

    protected PredictionEngine(AuthoritativeMotionState state, MovementWorldView world,
                               MovementCollisionEngine collisions, float correctionThreshold) {
        this.state = state;
        this.world = world;
        this.collisions = collisions;
        this.correctionThreshold = correctionThreshold;
    }

    public abstract MovementSimulator.SimulationResult run();

    protected MovementCollisionEngine.MoveOutcome move() {
        return collisions.move(state, world, correctionThreshold);
    }

    protected void applyKnockback() {
        if (state.hasKnockback()) {
            state.velocity(state.knockback());
        }
    }

    /** The shared travel tick, in BDS system order. */
    protected MovementSimulator.SimulationResult travel(Medium medium) {
        boolean water = medium == Medium.WATER;
        boolean lava = medium == Medium.LAVA;
        boolean glide = medium == Medium.GLIDE;
        boolean wasInWater = state.inWater();
        boolean grounded = state.onGround();

        applyKnockback();

        MovementBlockView feet = blockAt(state.position());
        boolean onClimbable = climbing(feet);
        MovementBlockView under = blockUnder(0.1f);
        float blockFriction = under.friction();

        Climber climber = senseScaffolding(state.boundingBox());
        boolean descend = climber.descendable() && state.sneaking();
        if (descend) {
            setVerticalVelocity(-SCAFFOLDING_SPEED);
            state.fallDistance(0.0f);
        }
        boolean gravityBlockedByScaffold = (climber.inside() || climber.over()) && descend;
        state.descendingScaffold(descend);

        float friction = grounded ? 0.91f * blockFriction : 0.91f;
        float relativeSpeed;
        if (water) {
            relativeSpeed = waterAcceleration(grounded);
        } else if (lava) {
            relativeSpeed = world.lavaSpeed();
        } else if (grounded) {
            float accelerationFriction = blockFriction;
            // soul sand drag scales the acceleration friction only; soul speed skips it
            if (under.named("soul_sand") && !state.soulSpeed()) {
                accelerationFriction *= 1.225f;
            }
            relativeSpeed = MovementConstants.groundAcceleration(state.movementSpeed(),
                    accelerationFriction);
        } else {
            relativeSpeed = state.airSpeed();
        }

        boolean jump = state.pressingJump() || state.jumping()
                || (water || lava) && state.autoJumpingInWater();
        boolean jumpBlockedBySwim = state.swimAmount() > 0.0f && state.swimAmount() < 1.0f
                || state.swimming() && !state.headInWater();
        boolean scaffoldJump = jump && !jumpBlockedBySwim
                && (climber.inside() || climber.ascendable()) && !descend;
        if (scaffoldJump) {
            setVerticalVelocity(SCAFFOLDING_SPEED);
            state.jumpDelay(MovementConstants.JUMP_DELAY_TICKS);
        }
        if (jump && (water || lava)) {
            if (jumpBlockedBySwim) {
                if (water) setVerticalVelocity(0.0f);
            } else if (!scaffoldJump && !onClimbable) {
                setVerticalVelocity(state.velocity().y() + 0.04f);
            }
        }
        if (water && state.swimming() && !jump) {
            applySwimControl();
        }

        boolean climbJump = jump && !jumpBlockedBySwim && !scaffoldJump && onClimbable;
        boolean jumpInitiated = jump && !scaffoldJump && !jumpBlockedBySwim && !glide && !climbJump
                && state.onGround() && state.jumpDelay() == 0 && !water && !lava
                && groundJumpAllowed(climber, descend);
        if (jumpInitiated) {
            applyGroundJump();
        }

        // the jump impulse lands before the input acceleration
        if (glide) {
            applyGlide();
        } else {
            moveRelative(relativeSpeed);
        }

        if (climbJump) {
            setVerticalVelocity(MovementConstants.CLIMB_SPEED);
        }
        if (onClimbable && !water && !lava && !glide) {
            float y = state.velocity().y();
            if (y < -MovementConstants.CLIMB_SPEED) y = -MovementConstants.CLIMB_SPEED;
            // the sneak hold comes with climbable blocks, never with powder snow
            if (state.sneaking() && y < 0.0f && feet.climbable()) y = 0.0f;
            setVerticalVelocity(y);
        }
        if (water && state.sneaking()) {
            setVerticalVelocity(state.velocity().y() - 0.04f);
        }

        // block slowdown stored by the previous tick scales this move request
        boolean slowed = applyStoredBlockSlowdown();
        FloatBox startBox = state.boundingBox();
        FloatVector stored = state.velocity();
        MovementCollisionEngine.MoveOutcome outcome = move();
        if (slowed) {
            // the slowdown cleared the stored velocity: only the request moved the body
            stored = FloatVector.ZERO;
            state.velocity(FloatVector.ZERO);
        }

        float restitution = landingResponse(stored, outcome.surface());
        boolean bounceCorrection = Math.abs(restitution) > GRAVITY_EPSILON
                && Math.abs(outcome.resolved().y()) > GRAVITY_EPSILON;

        boolean autoClimb = !water && !lava && !glide && (state.collideX() || state.collideZ())
                && climbing(blockAt(state.position()));
        if (autoClimb) {
            setVerticalVelocity(MovementConstants.CLIMB_SPEED);
        }

        state.movement(state.velocity());

        FloatVector velocity = state.velocity();
        float x = velocity.x();
        float y = velocity.y();
        float z = velocity.z();
        if (water || lava) {
            if (water) {
                float retention = waterDrag(state.onGround());
                x = retention * x;
                y = y * 0.8f;
                z = retention * z;
                if (state.levitationLevel() > 0) {
                    y = levitate(y, state.levitationLevel());
                } else if (!state.swimming()) {
                    y = gravity(y, WATER_GRAVITY, bounceCorrection, outcome);
                }
            } else {
                x = x * 0.5f;
                y = y * 0.5f;
                z = z * 0.5f;
                if (state.levitationLevel() > 0) {
                    y = levitate(y, state.levitationLevel());
                } else {
                    y = gravity(y, LAVA_GRAVITY, bounceCorrection, outcome);
                }
            }
        } else if (!glide) {
            if (state.levitationLevel() > 0) {
                y = levitate(y, state.levitationLevel());
            } else if (!autoClimb && state.affectedByGravity() && !gravityBlockedByScaffold) {
                float acceleration = -MovementConstants.NORMAL_GRAVITY;
                if (y < 0.0f && state.slowFalling()) {
                    acceleration = -MovementConstants.SLOW_FALLING_GRAVITY;
                    state.fallDistance(0.0f);
                }
                y = gravity(y, acceleration, bounceCorrection, outcome);
            }
            if (!autoClimb && state.affectedByGravity()) {
                float lost = (1.0f - 0.98f) * 1.0f;
                float retention = 1.0f < lost ? 0.0f : 1.0f - (0.0f > lost ? 0.0f : lost);
                y = retention * y;
            }
            x = MovementConstants.damp(x, friction);
            z = MovementConstants.damp(z, friction);
        }

        if ((water || lava) && (state.collideX() || state.collideZ())) {
            float dy = ((startBox.minY() - state.position().y()) + 0.6f) + y;
            FloatBox probe = state.boundingBox().offset(new FloatVector(x, dy, z));
            float[] raw = {probe.minX(), probe.minY(), probe.minZ(),
                    probe.maxX(), probe.maxY(), probe.maxZ()};
            if (!FluidCurrent.touches(world, raw, 1) && !FluidCurrent.touches(world, raw, 2)
                    && noCollision(probe)) {
                y = 0.3f;
            }
        }

        // slime/honey onStandOn: one-sided vy < 0.1, a fast rise is not damped
        if (state.onGround() && !state.sneaking() && !(y >= 0.1f)) {
            MovementBlockView stand = world.block(floor(state.position().x()),
                    (int) Math.floor((double) state.position().y() - 0.5),
                    floor(state.position().z()));
            if (stand.named("slime") || stand.named("honey_block")) {
                float factor = Math.abs(y) * 0.2f + 0.4f;
                x = x * factor;
                z = z * factor;
            }
        }
        state.velocity(new FloatVector(x, y, z));

        endSpinAttack();
        Climber after = senseScaffolding(state.boundingBox());
        if (!after.overPowderSnow()
                && (after.inside() || after.over() || after.ascendable() || after.descendable())) {
            state.fallDistance(0.0f);
        }
        applyBlockContacts(state, world, wasInWater);
        state.swimAmount(state.swimming() ? Math.min(state.swimAmount() + 0.1f, 1.0f)
                : Math.max(state.swimAmount() - 0.1f, 0.0f));
        return result(true, water || lava);
    }

    private float gravity(float y, float acceleration, boolean bounceCorrection,
                          MovementCollisionEngine.MoveOutcome outcome) {
        return bounceCorrection
                ? correctedGravity(y, acceleration, outcome.requested().y(), outcome.resolved().y())
                : acceleration + y;
    }

    static float levitate(float velocity, int level) {
        return velocity * 0.8f + (float) level * 0.01f;
    }

    /** BDS water acceleration: depth strider level clamped to [0, 3], halved off the ground. */
    private float waterAcceleration(boolean grounded) {
        float speed = state.movementSpeed();
        float acceleration = world.underwaterSpeed();
        float level = MovementConstants.clamp(state.depthStrider(), 0.0f, 3.0f);
        if (level > 0.0f) {
            if (!grounded) level *= 0.5f;
            float difference = speed - acceleration;
            float product = difference * level;
            acceleration += product / 3.0f;
        }
        return acceleration;
    }

    /** BDS water drag: 0.8 (0.9 sprinting), pulled toward 0.546 by depth strider. */
    private float waterDrag(boolean grounded) {
        float retention = state.sprinting() ? 0.9f : 0.8f;
        float level = MovementConstants.clamp(state.depthStrider(), 0.0f, 3.0f);
        if (!grounded) level *= 0.5f;
        float ratio = level / 3.0f;
        float difference = 0.54600006f - retention;
        return ratio * difference + retention;
    }

    /** Swim control from the interpolated pitch through the current sine table. */
    private void applySwimControl() {
        FloatVector previous = state.lastRotation() == null ? state.rotation() : state.lastRotation();
        float pitch = GlidePredictionEngine.interpolateAngle(previous.x(), state.rotation().x(), 1.0f);
        float coordinate = (pitch * -0.017453292f) * 10_430.378f;
        float look = MovementConstants.sineEntry(coordinate);
        float gain = look < -0.2f ? 0.085f : 0.06f;
        float vy = state.velocity().y();
        if (look <= 0.0f || state.headInWater()) {
            setVerticalVelocity(vy + (look - vy) * gain);
        } else {
            setVerticalVelocity(0.0f);
        }
    }

    private void applyGlide() {
        FloatVector previous = state.lastRotation() == null ? state.rotation() : state.lastRotation();
        FloatVector current = state.rotation();
        if (state.velocity().y() > -0.5f) {
            state.fallDistance(1.0f);
        }
        state.velocity(GlidePredictionEngine.glide(state.velocity(), previous.x(), previous.z(),
                current.x(), current.z(), state.slowFalling(), state.glideBoostTicks() > 0));
    }

    /** Riptide end: -0.2 per mob touched by the box grown by 1, a wall, 20 ticks, or landing. */
    private void endSpinAttack() {
        if (!state.riptideActive()) {
            return;
        }
        int hits = world.mobHitCount(state.boundingBox().grow(1.0f, 1.0f, 1.0f));
        if (hits > 0) {
            for (int i = 0; i < hits; i++) {
                FloatVector v = state.velocity();
                state.velocity(new FloatVector(v.x() * -0.2f, v.y() * -0.2f, v.z() * -0.2f));
            }
            state.stopRiptide();
            return;
        }
        state.finishRiptideTick();
    }

    private void applyGroundJump() {
        FloatVector velocity = state.velocity();
        float x = velocity.x();
        float y = velocity.y();
        float z = velocity.z();
        float height = state.jumpHeight() * jumpPreventionMultiplier();
        if (height > y) {
            y = height;
        }
        state.jumpDelay(MovementConstants.JUMP_DELAY_TICKS);
        if (state.sprinting()) {
            float coordinate = (state.rotation().z() * 0.017453292f) * 10_430.378f;
            x = x - MovementConstants.sineEntry(coordinate) * 0.2f;
            z = z + MovementConstants.sineEntry(coordinate + 16_384.0f) * 0.2f;
        }
        state.velocity(new FloatVector(x, y, z));
    }

    /**
     * BDS ground jump permission: scaffolding blocks it only while descending through it, and
     * powder snow under minY - 0.2 blocks it without leather boots.
     */
    private boolean groundJumpAllowed(Climber climber, boolean descend) {
        boolean scaffoldAllows = !(climber.inside() || climber.over()) || !descend;
        boolean blockedBySnow = jumpOnPowderSnow() && !state.wearingLeatherBoots();
        return scaffoldAllows && !blockedBySnow;
    }

    private boolean jumpOnPowderSnow() {
        FloatBox box = state.boundingBox();
        float probeY = box.minY() - 0.2f;
        FloatBox probe = new FloatBox(box.minX(), probeY, box.minZ(), box.maxX(), probeY, box.maxZ());
        return noCollision(probe) && blockAt(state.position()).named("powder_snow");
    }

    private boolean noCollision(FloatBox box) {
        for (FloatBox other : world.collisionBoxes(box)) {
            if (box.intersects(other)) return false;
        }
        return true;
    }

    protected void setVerticalVelocity(float y) {
        FloatVector velocity = state.velocity();
        state.velocity(new FloatVector(velocity.x(), y, velocity.z()));
    }

    /** Climbable block, or powder snow the body can stand on (leather boots). */
    protected boolean climbing(MovementBlockView block) {
        if (block.scaffoldingStability() >= 0 || block.named("scaffolding")) return false;
        return block.climbable() || block.named("powder_snow") && state.wearingLeatherBoots();
    }

    private MovementBlockView blockAt(FloatVector position) {
        return world.block(floor(position.x()), floor(position.y()), floor(position.z()));
    }

    /**
     * Vertical landing: slime (1.0) and bed (0.75) restitution for falls of at least 0x3da3d71a
     * while not sneaking, then vy = 0 on every vertical collision, then the impulse is added.
     * Returns the applied restitution, 0 when none.
     */
    private float landingResponse(FloatVector stored, MovementBlockPosition surfacePosition) {
        if (!state.collideY()) {
            return 0.0f;
        }
        MovementBlockView surface = surfacePosition == null ? MovementBlockView.AIR
                : world.block(surfacePosition.x(), surfacePosition.y(), surfacePosition.z());
        float restitution = 0.0f;
        float vy = stored.y();
        boolean bounces = vy < 0.0f && !(MovementConstants.MINIMUM_BOUNCE_SPEED > Math.abs(vy));
        boolean slime = surface.named("slime");
        boolean bed = surface.named("bed");
        if ((slime || bed) && !state.sneaking() && bounces) {
            float coefficient = bed ? 0.75f : 1.0f;
            restitution = Math.max(-(vy * coefficient), 0.0f);
        }
        setVerticalVelocity(restitution + 0.0f);
        return restitution;
    }

    /** Gravity only for the part of the tick after the impact (BDS bounce gravity correction). */
    static float correctedGravity(float velocity, float acceleration, float requested, float resolved) {
        float g = Math.abs(acceleration);
        if (!(g > GRAVITY_EPSILON)) {
            return acceleration + velocity;
        }
        float sign = 0.0f < requested ? 1.0f : (requested < 0.0f ? -1.0f : 0.0f);
        float root = (float) Math.sqrt((g + g) * Math.abs(resolved) + requested * requested);
        float impact = Math.abs((Math.abs(requested) - root) / acceleration);
        return ((1.0f - impact) * sign) * g + velocity;
    }

    /** Applies and clears the stored block slowdown multiplier; true when one was active. */
    private boolean applyStoredBlockSlowdown() {
        float mx = state.stuckX();
        float my = state.stuckY();
        float mz = state.stuckZ();
        if (mx == 0.0f && my == 0.0f && mz == 0.0f) {
            return false;
        }
        FloatVector velocity = state.velocity();
        state.velocity(new FloatVector(mx * velocity.x(), my * velocity.y(), mz * velocity.z()));
        state.stuckMultiplier(0.0f, 0.0f, 0.0f);
        return true;
    }

    /** Scaffolding and powder snow contacts at the feet layer and the layer below. */
    protected record Climber(boolean inside, boolean over, boolean ascendable, boolean descendable,
                             boolean overPowderSnow) {
    }

    private enum Cell { OTHER, SCAFFOLD, AIR, WATER, POWDER_SNOW }

    private Cell cell(int x, int y, int z) {
        MovementBlockView block = world.block(x, y, z);
        if (block.scaffoldingStability() >= 0 || block.named("scaffolding")) return Cell.SCAFFOLD;
        if (block.named("powder_snow")) return Cell.POWDER_SNOW;
        if (block.fluid() == 1) return Cell.WATER;
        return block.air() && block.empty() ? Cell.AIR : Cell.OTHER;
    }

    /** BDS SenseScaffolding. */
    protected Climber senseScaffolding(FloatBox body) {
        boolean canStandOnSnow = state.wearingLeatherBoots();
        int minX = floor(Math.min(body.minX(), body.maxX()));
        int maxX = floor(Math.max(body.minX(), body.maxX()));
        int minZ = floor(Math.min(body.minZ(), body.maxZ()));
        int maxZ = floor(Math.max(body.minZ(), body.maxZ()));
        boolean[] feet = sample(floor(body.minY()), minX, maxX, minZ, maxZ, canStandOnSnow);
        boolean[] below = sample(floor(body.minY() - 1.0f), minX, maxX, minZ, maxZ, canStandOnSnow);
        return new Climber(feet[0], below[0], feet[1], below[1], below[2]);
    }

    private boolean[] sample(int y, int minX, int maxX, int minZ, int maxZ, boolean canStandOnSnow) {
        boolean contact = false;
        boolean ascendable = false;
        boolean selectedPowder = false;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                Cell cell = cell(x, y, z);
                boolean eligible = false;
                if (cell == Cell.SCAFFOLD) {
                    contact = true;
                    Cell under = cell(x, y - 1, z);
                    eligible = under != Cell.AIR && under != Cell.WATER;
                }
                if (cell == Cell.POWDER_SNOW && canStandOnSnow) eligible = true;
                if (eligible && !ascendable) selectedPowder = cell == Cell.POWDER_SNOW;
                ascendable |= eligible;
            }
        }
        return new boolean[]{contact, ascendable, selectedPowder};
    }

    protected void moveRelative(float speed) {
        float forward = state.impulseForward();
        float sideways = state.impulseSideways();
        float force = sideways * sideways + forward * forward;
        if (!(force >= 1.0E-4f)) {
            return;
        }
        // BDS 1.26.52 relative movement: CRT sinf/cosf of yaw * 0.017453292f
        float scale = speed / Math.max((float) Math.sqrt(force), 1.0f);
        sideways = scale * sideways;
        forward = scale * forward;
        float yaw = state.rotation().z() * MovementConstants.DEGREES_TO_RADIANS;
        float sine = MovementConstants.crtSin(yaw);
        float cosine = MovementConstants.crtCos(yaw);
        FloatVector velocity = state.velocity();
        state.velocity(new FloatVector((cosine * sideways - sine * forward) + velocity.x(),
                velocity.y(), (cosine * forward + sine * sideways) + velocity.z()));
    }

    protected MovementBlockView blockUnder(float distance) {
        FloatVector position = state.position().add(0.0f, -distance, 0.0f);
        return world.block(floor(position.x()), floor(position.y()), floor(position.z()));
    }

    protected float jumpPreventionMultiplier() {
        return jumpPreventionMultiplier(state, world);
    }

    /**
     * BDS ground jump: the block at floor(minY), else the one below when the body rests on the
     * block boundary.
     */
    static float jumpPreventionMultiplier(AuthoritativeMotionState state, MovementWorldView world) {
        if (!state.onGround()) {
            return 1.0f;
        }
        FloatBox box = state.boundingBox();
        int x = floor(state.position().x());
        int z = floor(state.position().z());
        int feetY = floor(box.minY());
        if (world.block(x, feetY, z).preventsJumping()) {
            return MovementConstants.PREVENTED_JUMP_MULTIPLIER;
        }
        if ((float) feetY >= box.minY() && world.block(x, feetY - 1, z).preventsJumping()) {
            return MovementConstants.PREVENTED_JUMP_MULTIPLIER;
        }
        return 1.0f;
    }

    protected MovementSimulator.SimulationResult result(boolean reliable) {
        return result(reliable, false);
    }

    protected MovementSimulator.SimulationResult result(boolean reliable, boolean inFluid) {
        return new MovementSimulator.SimulationResult(state.position(), state.velocity(),
                state.movement(), state.onGround(), state.collideX(), state.collideY(),
                state.collideZ(), reliable, inFluid);
    }

    protected static int floor(float value) {
        return (int) Math.floor(value);
    }

    /**
     * End-of-tick block contacts (BDS order): bubble columns per cell, honey wall slide per honey
     * cell unless the body was in water, and the cobweb / powder snow / berry bush slowdown stored
     * for the next move, combined as the per-axis minimum.
     */
    static void applyBlockContacts(AuthoritativeMotionState state, MovementWorldView world,
                                   boolean wasInWater) {
        FloatBox box = state.boundingBox();
        int minimumX = (int) Math.floor(box.minX() + 0.001f);
        int minimumY = (int) Math.floor(box.minY() + 0.001f);
        int minimumZ = (int) Math.floor(box.minZ() + 0.001f);
        int maximumX = (int) Math.floor(box.maxX() - 0.001f);
        int maximumY = (int) Math.floor(box.maxY() - 0.001f);
        int maximumZ = (int) Math.floor(box.maxZ() - 0.001f);
        boolean web = false;
        boolean powderSnow = false;
        boolean berryBush = false;
        java.util.List<int[]> honey = new java.util.ArrayList<>();
        for (int x = minimumX; x <= maximumX; x++) {
            for (int y = minimumY; y <= maximumY; y++) {
                for (int z = minimumZ; z <= maximumZ; z++) {
                    MovementBlockView block = world.block(x, y, z);
                    web |= block.named("web");
                    powderSnow |= block.named("powder_snow");
                    berryBush |= block.named("sweet_berry_bush");
                    if (block.bubble() != 0) {
                        MovementBlockView above = world.block(x, y + 1, z);
                        boolean surface = above.air() && above.fluid() == 0 && above.empty();
                        FloatVector v = state.velocity();
                        state.fallDistance(0.0f);
                        state.velocity(new FloatVector(v.x(),
                                bubbleColumn(v.y(), block.bubble() < 0, surface), v.z()));
                    }
                    if (block.named("honey_block")) honey.add(new int[]{x, y, z});
                }
            }
        }
        if (!wasInWater) {
            float width = state.boundingBox().maxX() - state.boundingBox().minX();
            for (int[] cell : honey) {
                FloatVector v = state.velocity();
                FloatVector p = state.position();
                v = new FloatVector(v.x() * 0.4f, Math.max(v.y(), -0.12f), v.z() * 0.4f);
                state.velocity(v);
                if ((float) cell[1] + 0.9375f >= p.y() && v.y() < 0.0f) {
                    float radius = width * 0.5f + 0.43125f;
                    float dx = ((float) cell[0] + 0.5f) - p.x();
                    float dz = ((float) cell[2] + 0.5f) - p.z();
                    if (Math.abs(dx) > radius || Math.abs(dz) > radius) {
                        state.fallDistance(0.0f);
                    }
                }
            }
        }
        if (!web && !powderSnow && !berryBush) {
            return;
        }
        float[] multiplier = {state.stuckX(), state.stuckY(), state.stuckZ()};
        Boolean[] first = {null};
        if (web) accumulate(multiplier, 0.25f, 0.05f, 0.25f, first);
        if (powderSnow) accumulate(multiplier, 0.9f, 1.5f, 0.9f, first);
        if (berryBush) accumulate(multiplier, 0.8f, 0.75f, 0.8f, first);
        if (first[0] != null && first[0] && state.weaving() && web && !powderSnow && !berryBush) {
            multiplier = new float[]{0.5f, 0.25f, 0.5f};
        }
        if (first[0] != null && first[0]) {
            state.fallDistance(0.0f);
        }
        state.stuckMultiplier(multiplier[0], multiplier[1], multiplier[2]);
    }

    /** BDS bubble column velocity: -0.03 / +0.06 (+0.1 at the surface), clamped. */
    static float bubbleColumn(float velocity, boolean dragDown, boolean surface) {
        float next = velocity + (dragDown ? -0.03f : surface ? 0.1f : 0.06f);
        if (dragDown) {
            float limit = surface ? -0.9f : -0.3f;
            return next < limit ? limit : next;
        }
        float limit = surface ? 1.8f : 0.7f;
        return next > limit ? limit : next;
    }

    private static void accumulate(float[] multiplier, float x, float y, float z, Boolean[] first) {
        float epsilon = MovementConstants.FLT_EPSILON;
        boolean existing = Math.abs(multiplier[0]) >= epsilon || Math.abs(multiplier[1]) >= epsilon
                || Math.abs(multiplier[2]) >= epsilon;
        if (first[0] == null) first[0] = !existing;
        if (!existing) {
            multiplier[0] = x;
            multiplier[1] = y;
            multiplier[2] = z;
        } else {
            if (multiplier[0] > x) multiplier[0] = x;
            if (multiplier[1] > y) multiplier[1] = y;
            if (multiplier[2] > z) multiplier[2] = z;
        }
    }
}
