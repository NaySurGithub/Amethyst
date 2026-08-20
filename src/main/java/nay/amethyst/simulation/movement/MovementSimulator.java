package nay.amethyst.simulation.movement;

public final class MovementSimulator {
    private static final float DEGREES_TO_RADIANS = (float) Math.PI / 180.0f;
    private static final float WATER_DRAG = 0.8f;
    static final float WATER_ACCELERATION = 0.02f;
    private static final float WATER_GRAVITY = 0.02f;
    private static final float WATER_ASCENT = 0.04f;
    private static final float WATER_PUSH = 0.014f;
    private static final float BUBBLE_DOWNWARD_MAX = -0.3f;
    private static final float BUBBLE_DOWNWARD_EXIT_MAX = -0.9f;
    private static final float BUBBLE_DOWNWARD_ACCELERATION = 0.03f;
    private static final float BUBBLE_UPWARD_MAX = 0.7f;
    private static final float BUBBLE_UPWARD_EXIT_MAX = 1.8f;
    private static final float BUBBLE_UPWARD_ACCELERATION = 0.08f;
    private static final float BUBBLE_UPWARD_EXIT_ACCELERATION = 0.1f;

    private final MovementCollisionEngine collisions = new MovementCollisionEngine();
    private final boolean simulateWater;

    public MovementSimulator(boolean simulateWater) {
        this.simulateWater = simulateWater;
    }

    public SimulationResult simulate(AuthoritativeMotionState state,
                                     MovementWorldView world,
                                     float correctionThreshold) {
        if (attemptTeleport(state)) {
            state.movement(state.velocity());
            return result(state, true);
        }

        if (!reliable(state, world)) {
            state.resetToClient();
            return result(state, false);
        }
        if (!world.contains(state.boundingBox().grow(1.0f, 1.0f, 1.0f))) {
            state.resetToClient();
            return result(state, false);
        }
        if (state.immobile() || !state.ready()) {
            state.velocity(FloatVector.ZERO);
            return result(state, false);
        }
        if (state.velocity().lengthSquared() < 1.0E-12f) {
            state.velocity(FloatVector.ZERO);
        }

        FluidState fluid = world.fluidState(state.boundingBox());
        if (simulateWater && fluid.water()) {
            simulateSwim(state, world, fluid, correctionThreshold);
            state.movement(state.velocity());
            return result(state, true, true);
        }

        MovementBlockView blockUnder = blockUnder(state, world, 0.5f);
        float friction = MovementConstants.AIR_FRICTION;
        float acceleration = state.airSpeed();
        if (state.onGround()) {
            float speed = state.movementSpeed();
            if (blockUnder.named("soul_sand")) {
                speed *= 0.543f;
            }
            friction *= blockUnder.friction();
            acceleration = speed * (0.16277136f / (friction * friction * friction));
        }

        if (state.gliding()) {
            if (state.wearingElytra() && !state.onGround()) {
                state.onGround(false);
                simulateGlide(state, world, correctionThreshold);
                state.movement(state.velocity());
                return result(state, true);
            }
            state.gliding(false);
        }

        applyKnockback(state);
        moveRelative(state, acceleration);
        applyJump(state);
        applyClimbable(state, world);

        boolean cobweb = insideCobweb(state, world);
        if (cobweb) {
            state.velocity(state.velocity().multiply(0.25f, 0.05f, 0.25f));
        } else if (insideBlockNamed(state, world, "powder_snow")) {
            state.velocity(state.velocity().multiply(0.9f, 1.5f, 0.9f));
        } else if (insideBlockNamed(state, world, "sweet_berry_bush")) {
            state.velocity(state.velocity().multiply(0.8f, 0.75f, 0.8f));
        }

        FloatVector oldVelocity = state.velocity();
        boolean oldOnGround = state.onGround();
        float oldY = state.position().y();
        collisions.move(state, world, correctionThreshold);

        if (state.hasSupportingBlock()) {
            blockUnder = world.block(state.supportingBlockX(), state.supportingBlockY(),
                    state.supportingBlockZ());
        } else {
            blockUnder = blockUnder(state, world, 0.2f);
            if (blockUnder.air()) {
                MovementBlockView below = world.block(floor(state.position().x()),
                        floor(state.position().y()) - 1, floor(state.position().z()));
                if (below.id().contains("wall") || below.id().contains("fence")) {
                    blockUnder = below;
                }
            }
        }

        if (oldY == state.position().y()) {
            walkOnBlock(state, blockUnder);
        }
        state.movement(state.velocity());
        postCollisionMotion(state, oldVelocity, oldOnGround, blockUnder);

        // The cooldown exists to stop a second jump in the same hop, not to hold the player down once
        // they are back on the ground: a client with jump held jumps again the tick it lands, and
        // only releasing the key cleared this otherwise.
        if (!oldOnGround && state.onGround()) {
            state.jumpDelay(0);
        }

        if (cobweb) {
            state.velocity(FloatVector.ZERO);
        }

        if (recoverSupportedClientBranch(state, world, correctionThreshold)) {
            state.resetToClient();
            state.onGround(true);
            state.clearSupportingBlock();
            return result(state, false);
        }

        if (recoverDepartedEdge(state, world)) {
            state.resetToClient();
            state.onGround(false);
            state.clearSupportingBlock();
            return result(state, false);
        }

        FloatVector velocity = state.velocity();
        float x = velocity.x();
        float y = velocity.y();
        float z = velocity.z();
        if (state.levitationLevel() > 0) {
            float levitationSpeed = MovementConstants.LEVITATION_MULTIPLIER
                    * state.levitationLevel();
            y += (levitationSpeed - y) * 0.2f;
        } else if (state.affectedByGravity()) {
            y -= state.gravity();
            y *= MovementConstants.GRAVITY_MULTIPLIER;
        }
        x *= friction;
        z *= friction;
        state.velocity(new FloatVector(x, y, z));
        return result(state, true);
    }

    private static boolean recoverSupportedClientBranch(AuthoritativeMotionState state,
                                                         MovementWorldView world,
                                                         float correctionThreshold) {
        FloatVector difference = state.position().subtract(state.client().position());
        float verticalDifference = Math.abs(difference.y());
        if (difference.length() <= correctionThreshold
                || difference.horizontalLengthSquared() > 0.49f
                || verticalDifference > 1.05f
                || state.client().movement().horizontalLengthSquared() > 0.64f) {
            return false;
        }

        boolean sameLevel = verticalDifference <= 0.08f;
        boolean steppedUp = difference.y() < -0.3f
                && state.client().movement().y() > 0.08f;
        if (!sameLevel && !steppedUp) {
            return false;
        }
        if (!hasSupport(world, state.boundingBox(), 0.08f)
                || !hasSupport(world, state.clientBoundingBox(), 0.08f)
                || !world.collisionBoxes(state.clientBoundingBox()).isEmpty()) {
            return false;
        }
        return state.claimSupportedBranchRecovery();
    }

    private static boolean recoverDepartedEdge(AuthoritativeMotionState state,
                                                MovementWorldView world) {
        FloatVector difference = state.position().subtract(state.client().position());
        if (difference.y() <= 0.3f
                || difference.horizontalLengthSquared() > 1.0f) {
            return false;
        }

        FloatVector clientMovement = state.client().movement();
        FloatVector clientVelocity = state.client().velocity();
        if (clientMovement.y() >= -0.08f && clientVelocity.y() >= -0.08f) {
            return false;
        }

        return hasSupport(world, state.boundingBox(), 0.65f)
                && !hasSupport(world, state.clientBoundingBox(), 0.08f);
    }

    private static boolean hasSupport(MovementWorldView world, FloatBox box,
                                      float depth) {
        FloatBox probe = new FloatBox(
                box.minX() + 0.03f,
                box.minY() - depth,
                box.minZ() + 0.03f,
                box.maxX() - 0.03f,
                box.minY() + 0.02f,
                box.maxZ() - 0.03f
        );
        return !world.collisionBoxes(probe).isEmpty();
    }

    public boolean reliable(AuthoritativeMotionState state, MovementWorldView world) {
        if (state.hasTeleport()) {
            return true;
        }
        FluidState fluid = world.fluidState(state.boundingBox());
        if (fluid.lava() || fluid.water() && !simulateWater
                || world.hasMovingBlock(state.boundingBox())
                || world.hasSolidEntityNearby(state.boundingBox())
                || world.hasBambooNearby(state.boundingBox())
                || world.hasScaffoldingIntersection(state.boundingBox())
                || world.hasScaffoldingIntersection(state.clientBoundingBox())
                // a block placed where the player stands leaves the client inside it, and how it
                // pushes out is the client's own affair, not something to reproduce
                || !world.collisionBoxes(state.clientBoundingBox()).isEmpty()) {
            return false;
        }
        return !state.flying() && !state.justDisabledFlight() && !state.noClip()
                && state.alive() && state.supportedGameMode();
    }

    private void simulateGlide(AuthoritativeMotionState state, MovementWorldView world,
                               float correctionThreshold) {
        float yaw = state.rotation().z() * DEGREES_TO_RADIANS;
        float pitch = state.rotation().x() * DEGREES_TO_RADIANS;
        float yawCosine = MovementConstants.cos(-yaw - (float) Math.PI);
        float yawSine = MovementConstants.sin(-yaw - (float) Math.PI);
        float pitchCosine = MovementConstants.cos(pitch);
        float pitchSine = MovementConstants.sin(pitch);

        float lookX = yawSine * -pitchCosine;
        float lookY = -pitchSine;
        float lookZ = yawCosine * -pitchCosine;
        FloatVector velocity = state.velocity();
        float x = velocity.x();
        float y = velocity.y();
        float z = velocity.z();
        float horizontalVelocity = (float) Math.sqrt(x * x + z * z);
        float horizontalLook = pitchCosine;
        float squaredPitchCosine = pitchCosine * pitchCosine;

        y += -0.08f + squaredPitchCosine * 0.06f;
        if (y < 0.0f && horizontalLook > 0.0f) {
            float acceleration = y * -0.1f * squaredPitchCosine;
            y += acceleration;
            x += lookX * acceleration / horizontalLook;
            z += lookZ * acceleration / horizontalLook;
        }
        if (pitch < 0.0f) {
            float acceleration = horizontalVelocity * -pitchSine * 0.04f;
            y += acceleration * 3.2f;
            x -= lookX * acceleration / horizontalLook;
            z -= lookZ * acceleration / horizontalLook;
        }
        if (horizontalLook > 0.0f) {
            x += (lookX / horizontalLook * horizontalVelocity - x) * 0.1f;
            z += (lookZ / horizontalLook * horizontalVelocity - z) * 0.1f;
        }
        if (state.glideBoostTicks() > 0) {
            x += lookX * 0.1f + (lookX * 1.5f - x) * 0.5f;
            y += lookY * 0.1f + (lookY * 1.5f - y) * 0.5f;
            z += lookZ * 0.1f + (lookZ * 1.5f - z) * 0.5f;
        }
        state.velocity(new FloatVector(x * 0.99f, y * 0.98f, z * 0.99f));
        collisions.move(state, world, correctionThreshold);
    }

    /**
     * Swimming runs on its own drag and acceleration instead of block friction, and the current of a
     * flowing block pushes on top of it. This is deliberately kept out of the setback path: it is
     * close enough to spot flight or speed under water, not close enough to rubber-band on.
     */
    private void simulateSwim(AuthoritativeMotionState state, MovementWorldView world,
                              FluidState fluid, float correctionThreshold) {
        applyKnockback(state);

        FloatVector flow = fluid.flow();
        if (flow.lengthSquared() > 0.0f) {
            float length = flow.length();
            state.velocity(state.velocity().add(
                    flow.x() / length * WATER_PUSH,
                    flow.y() / length * WATER_PUSH,
                    flow.z() / length * WATER_PUSH));
        }

        moveRelative(state, world.underwaterSpeed());
        if (state.pressingJump()) {
            state.velocity(state.velocity().add(0.0f, WATER_ASCENT, 0.0f));
        }

        collisions.move(state, world, correctionThreshold);

        FloatVector velocity = state.velocity();
        float drag = state.sprinting() ? 0.9f : WATER_DRAG;
        state.velocity(new FloatVector(velocity.x() * drag,
                velocity.y() * WATER_DRAG - WATER_GRAVITY, velocity.z() * drag));

        applyBubbleColumn(state, fluid);
    }

    /**
     * A bubble column overrides the vertical velocity outright rather than adding to it, and throws
     * harder on the block where it breaks the surface.
     */
    private static void applyBubbleColumn(AuthoritativeMotionState state, FluidState fluid) {
        if (fluid.bubbleDirection() == 0) {
            return;
        }

        FloatVector velocity = state.velocity();
        float y = Math.max(velocity.y(), BUBBLE_DOWNWARD_MAX);
        if (fluid.bubbleDirection() < 0) {
            y = Math.max(fluid.bubbleSurface() ? BUBBLE_DOWNWARD_EXIT_MAX : BUBBLE_DOWNWARD_MAX,
                    y - BUBBLE_DOWNWARD_ACCELERATION);
        } else {
            y = fluid.bubbleSurface()
                    ? Math.min(BUBBLE_UPWARD_EXIT_MAX, y + BUBBLE_UPWARD_EXIT_ACCELERATION)
                    : Math.min(BUBBLE_UPWARD_MAX, y + BUBBLE_UPWARD_ACCELERATION);
        }

        state.velocity(new FloatVector(velocity.x(), y, velocity.z()));
    }

    private static boolean attemptTeleport(AuthoritativeMotionState state) {
        if (!state.hasTeleport()) {
            return false;
        }
        if (!state.teleportSmoothed()) {
            state.position(state.teleportPosition());
            state.velocity(FloatVector.ZERO);
            state.jumpDelay(0);
            applyJump(state);
            return true;
        }
        FloatVector difference = state.teleportPosition().subtract(state.position());
        int remaining = state.teleportCompletionTicks() - (int) state.ticksSinceTeleport() + 1;
        if (remaining > 0) {
            state.position(state.position().add(difference.multiply(1.0f / remaining)));
            state.jumpDelay(0);
            return remaining > 1;
        }
        return false;
    }

    private static void applyKnockback(AuthoritativeMotionState state) {
        if (state.hasKnockback()) {
            state.velocity(state.knockback());
        }
    }

    private static void applyJump(AuthoritativeMotionState state) {
        if (!state.jumping() || !state.onGround() || state.jumpDelay() > 0) {
            return;
        }
        FloatVector velocity = state.velocity();
        float x = velocity.x();
        float y = Math.max(state.jumpHeight(), velocity.y());
        float z = velocity.z();
        state.jumpDelay(MovementConstants.JUMP_DELAY_TICKS);
        if (state.sprinting()) {
            float direction = state.rotation().z() * 0.017453292f;
            x -= MovementConstants.sin(direction) * 0.2f;
            z += MovementConstants.cos(direction) * 0.2f;
        }
        state.velocity(new FloatVector(x, y, z));
    }

    private static void moveRelative(AuthoritativeMotionState state, float speed) {
        float forward = state.impulseForward();
        float sideways = state.impulseSideways();
        float force = forward * forward + sideways * sideways;
        if (force < 1.0E-4f) {
            return;
        }
        force = speed / Math.max((float) Math.sqrt(force), 1.0f);
        forward *= force;
        sideways *= force;
        float yaw = state.rotation().z() * (float) Math.PI / 180.0f;
        float sine = MovementConstants.sin(yaw);
        float cosine = MovementConstants.cos(yaw);
        FloatVector velocity = state.velocity();
        state.velocity(velocity.add(sideways * cosine - forward * sine, 0.0f,
                forward * cosine + sideways * sine));
    }

    private static void applyClimbable(AuthoritativeMotionState state,
                                       MovementWorldView world) {
        MovementBlockView block = world.block(floor(state.position().x()),
                floor(state.position().y()), floor(state.position().z()));
        if (!block.climbable()) {
            return;
        }
        FloatVector velocity = state.velocity();
        float y = Math.max(velocity.y(), -MovementConstants.CLIMB_SPEED);
        if (state.pressingJump() || state.collideX() || state.collideZ()) {
            y = MovementConstants.CLIMB_SPEED;
        }
        if (state.sneaking() && y < 0.0f) {
            y = 0.0f;
        }
        state.velocity(new FloatVector(velocity.x(), y, velocity.z()));
    }

    private static boolean insideCobweb(AuthoritativeMotionState state,
                                        MovementWorldView world) {
        return insideBlockNamed(state, world, "web");
    }

    private static boolean insideBlockNamed(AuthoritativeMotionState state,
                                            MovementWorldView world, String name) {
        FloatBox box = state.boundingBox();
        int minimumX = floor(box.minX() - 1.0f);
        int minimumY = floor(box.minY() - 1.0f);
        int minimumZ = floor(box.minZ() - 1.0f);
        int maximumX = floor(box.maxX() + 1.0f);
        int maximumY = floor(box.maxY() + 1.0f);
        int maximumZ = floor(box.maxZ() + 1.0f);
        for (int x = minimumX; x <= maximumX; x++) {
            for (int y = minimumY; y <= maximumY; y++) {
                for (int z = minimumZ; z <= maximumZ; z++) {
                    if (!world.block(x, y, z).named(name)) {
                        continue;
                    }
                    FloatBox block = new FloatBox(x, y, z, x + 1.0f, y + 1.0f, z + 1.0f);
                    if (box.intersects(block)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static MovementBlockView blockUnder(AuthoritativeMotionState state,
                                                MovementWorldView world,
                                                float distance) {
        FloatVector position = state.position().add(0.0f, -distance, 0.0f);
        return world.block(floor(position.x()), floor(position.y()), floor(position.z()));
    }

    private static void walkOnBlock(AuthoritativeMotionState state,
                                    MovementBlockView block) {
        if (!state.onGround() || state.sneaking()) {
            return;
        }
        if (block.named("honey")) {
            FloatVector velocity = state.velocity();
            state.velocity(new FloatVector(velocity.x() * 0.4f, velocity.y(),
                    velocity.z() * 0.4f));
            return;
        }
        if (!block.named("slime")) {
            return;
        }
        FloatVector velocity = state.velocity();
        float vertical = Math.abs(velocity.y());
        if (vertical < 0.1f && !state.pressingSneak()) {
            float multiplier = 0.4f + vertical * 0.2f;
            state.velocity(new FloatVector(velocity.x() * multiplier, velocity.y(),
                    velocity.z() * multiplier));
        }
    }

    private static void postCollisionMotion(AuthoritativeMotionState state,
                                            FloatVector oldVelocity,
                                            boolean oldOnGround,
                                            MovementBlockView blockUnder) {
        FloatVector velocity = state.velocity();
        float x = state.collideX() ? 0.0f : velocity.x();
        float y = velocity.y();
        float z = state.collideZ() ? 0.0f : velocity.z();
        if (!oldOnGround && state.collideY()) {
            if (oldVelocity.y() >= 0.0f || state.pressingSneak()) {
                y = 0.0f;
            } else if (blockUnder.named("slime")) {
                y = -oldVelocity.y();
                if (Math.abs(y) < 1.0E-4f) y = 0.0f;
            } else if (blockUnder.named("bed")) {
                y = Math.min(1.0f, -0.66f * oldVelocity.y());
            } else {
                y = 0.0f;
            }
        } else if (state.collideY()) {
            y = 0.0f;
        }
        state.velocity(new FloatVector(x, y, z));
    }

    private static SimulationResult result(AuthoritativeMotionState state,
                                           boolean reliable) {
        return result(state, reliable, false);
    }

    private static SimulationResult result(AuthoritativeMotionState state,
                                           boolean reliable, boolean inFluid) {
        return new SimulationResult(state.position(), state.velocity(), state.movement(),
                state.onGround(), state.collideX(), state.collideY(), state.collideZ(), reliable,
                inFluid);
    }

    private static int floor(float value) {
        return (int) Math.floor(value);
    }

    public record SimulationResult(FloatVector position, FloatVector velocity,
                                   FloatVector movement, boolean onGround,
                                   boolean collideX, boolean collideY, boolean collideZ,
                                   boolean reliable, boolean inFluid) {
    }
}
