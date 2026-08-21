package nay.amethyst.simulation.movement;

/** Picks the prediction engine matching the player's medium and hands it the tick. */
public final class MovementSimulator {

    static final float WATER_ACCELERATION = WaterPredictionEngine.ACCELERATION;

    private final MovementCollisionEngine collisions = new MovementCollisionEngine();

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

        return engineFor(state, world, correctionThreshold).run();
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
        if (state.swimming() && world.fluidState(state.boundingBox()).water()) {
            return false;
        }
        if (world.hasMovingBlock(state.boundingBox())
                || world.hasSolidEntityNearby(state.boundingBox())
                || world.hasBambooNearby(state.boundingBox())
                || world.hasScaffoldingIntersection(state.boundingBox())
                || world.hasScaffoldingIntersection(state.clientBoundingBox())
                || !world.collisionBoxes(state.clientBoundingBox()).isEmpty()) {
            return false;
        }
        return !state.flying() && !state.justDisabledFlight() && !state.noClip()
                && state.alive() && state.supportedGameMode();
    }

    private static boolean attemptTeleport(AuthoritativeMotionState state) {
        if (!state.hasTeleport()) {
            return false;
        }
        if (!state.teleportSmoothed()) {
            state.position(state.teleportPosition());
            state.velocity(FloatVector.ZERO);
            state.jumpDelay(0);
            applyTeleportJump(state);
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

    private static void applyTeleportJump(AuthoritativeMotionState state) {
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

    private static SimulationResult result(AuthoritativeMotionState state, boolean reliable) {
        return new SimulationResult(state.position(), state.velocity(), state.movement(),
                state.onGround(), state.collideX(), state.collideY(), state.collideZ(), reliable,
                false);
    }

    public record SimulationResult(FloatVector position, FloatVector velocity,
                                   FloatVector movement, boolean onGround,
                                   boolean collideX, boolean collideY, boolean collideZ,
                                   boolean reliable, boolean inFluid) {
    }
}
