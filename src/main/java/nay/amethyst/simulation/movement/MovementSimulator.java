package nay.amethyst.simulation.movement;

/** Picks the prediction engine matching the player's medium and hands it the tick. */
public final class MovementSimulator {

    static final float WATER_ACCELERATION = WaterPredictionEngine.ACCELERATION;

    private final MovementCollisionEngine collisions = new MovementCollisionEngine();

    public SimulationResult simulate(AuthoritativeMotionState state,
                                     MovementWorldView world,
                                     float correctionThreshold) {
        if (attemptTeleport(state, world)) {
            state.movement(state.velocity());
            return finishRiptideTick(state, result(state, true));
        }

        if (!reliable(state, world)) {
            state.resetToClient();
            return finishRiptideTick(state, result(state, false));
        }
        if (!world.contains(state.boundingBox().grow(1.0f, 1.0f, 1.0f))) {
            state.resetToClient();
            return finishRiptideTick(state, result(state, false));
        }
        if (state.immobile() || !state.ready()) {
            state.velocity(FloatVector.ZERO);
            return finishRiptideTick(state, result(state, false));
        }
        if (state.velocity().lengthSquared() < 1.0E-12f) {
            state.velocity(FloatVector.ZERO);
        }

        applyRiptideGroundStep(state, world, correctionThreshold);
        SimulationResult result = engineFor(state, world, correctionThreshold).run();
        return finishRiptideTick(state, result);
    }

    private PredictionEngine engineFor(AuthoritativeMotionState state, MovementWorldView world,
                                       float correctionThreshold) {
        FluidState fluid = world.fluidState(state.boundingBox());
        if (fluid.water()) {
            return new WaterPredictionEngine(state, world, collisions, correctionThreshold, fluid);
        }
        if (fluid.lava()) {
            return new LavaPredictionEngine(state, world, collisions, correctionThreshold, fluid);
        }
        if (state.gliding()) {
            if (state.wearingElytra() && !state.onGround()) {
                state.onGround(false);
                return new GlidePredictionEngine(state, world, collisions, correctionThreshold);
            }
            state.gliding(false);
        }
        return new GroundAndAirPredictionEngine(state, world, collisions, correctionThreshold);
    }

    public boolean reliable(AuthoritativeMotionState state, MovementWorldView world) {
        if (state.hasTeleport()) {
            return true;
        }
        if (world.hasMovingBlock(state.boundingBox())
                || !state.riptideActive() && world.hasSolidEntityNearby(state.boundingBox())
                || world.hasBambooNearby(state.boundingBox())
                || world.hasScaffoldingIntersection(state.boundingBox())
                || world.hasScaffoldingIntersection(state.clientBoundingBox())
                || !world.collisionBoxes(state.clientBoundingBox()).isEmpty()) {
            return false;
        }
        return !state.flying() && !state.justDisabledFlight() && !state.noClip()
                && state.alive() && state.supportedGameMode();
    }

    private void applyRiptideGroundStep(AuthoritativeMotionState state, MovementWorldView world,
                                         float correctionThreshold) {
        if (!state.riptideGroundStepPending()) return;
        FloatVector velocity = state.hasKnockback() ? state.knockback() : state.velocity();
        state.velocity(velocity);
        state.velocity(new FloatVector(0.0f, 1.0f, 0.0f));
        collisions.move(state, world, correctionThreshold);
        state.velocity(velocity);
        state.consumeRiptideGroundStep();
    }

    private static boolean attemptTeleport(AuthoritativeMotionState state,
                                           MovementWorldView world) {
        if (!state.hasTeleport()) {
            return false;
        }
        if (!state.teleportSmoothed()) {
            state.position(state.teleportPosition());
            state.velocity(FloatVector.ZERO);
            state.jumpDelay(0);
            applyTeleportJump(state, world);
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

    private static void applyTeleportJump(AuthoritativeMotionState state,
                                          MovementWorldView world) {
        if (!state.jumping() || !state.onGround() || state.jumpDelay() > 0) {
            return;
        }
        FloatVector velocity = state.velocity();
        float x = velocity.x();
        FloatBox box = state.boundingBox();
        int xBlock = (int) Math.floor(state.position().x());
        int zBlock = (int) Math.floor(state.position().z());
        int feetY = (int) Math.floor(box.minY());
        boolean prevented = world.block(xBlock, feetY, zBlock).preventsJumping()
                || world.block(xBlock, feetY - 1, zBlock).preventsJumping();
        float multiplier = prevented ? MovementConstants.PREVENTED_JUMP_MULTIPLIER : 1.0f;
        float y = state.jumpHeight() * multiplier;
        float z = velocity.z();
        state.jumpDelay(MovementConstants.JUMP_DELAY_TICKS);
        if (state.sprinting()) {
            float direction = state.rotation().z() * 0.017453292f;
            x -= MovementConstants.sin(direction) * 0.2f;
            z += MovementConstants.cos(direction) * 0.2f;
        }
        state.velocity(new FloatVector(x, y, z));
    }

    private static SimulationResult result(AuthoritativeMotionState state, boolean reliable) {
        return new SimulationResult(state.position(), state.velocity(), state.movement(),
                state.onGround(), state.collideX(), state.collideY(), state.collideZ(), reliable,
                false);
    }

    private static SimulationResult finishRiptideTick(AuthoritativeMotionState state,
                                                        SimulationResult result) {
        state.finishRiptideTick();
        return result;
    }

    public record SimulationResult(FloatVector position, FloatVector velocity,
                                   FloatVector movement, boolean onGround,
                                   boolean collideX, boolean collideY, boolean collideZ,
                                   boolean reliable, boolean inFluid) {
    }
}
