package nay.amethyst.simulation.movement;

/** Picks the prediction engine matching the player's medium and hands it the tick. */
public final class MovementSimulator {

    public static final float WATER_ACCELERATION = WaterPredictionEngine.ACCELERATION;

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
        state.descendingScaffold(false);

        state.headInWater(headInWater(state, world));
        state.nearFluid(world.hasLiquidIntersection(state.boundingBox().grow(1.0f, 1.0f, 1.0f)));
        // swimming is the client's word: it holds only with liquid within reach
        if (state.swimming() && !state.nearFluid()) {
            state.swimming(false);
        }

        // glide input: +0.1 lift while gliding with jump held in creative, after 11 glide ticks
        boolean glideLift = state.gliding() && state.pressingJump() && state.instabuild()
                && state.fallFlyTicks() >= 11;
        state.fallFlyTicks(state.gliding() ? state.fallFlyTicks() + 1 : 0);
        if (glideLift) {
            FloatVector v = state.velocity();
            state.velocity(new FloatVector(v.x() + 0.0f, v.y() + 0.1f, v.z() + 0.0f));
        }
        boolean clientInsideCollider = !state.hasTeleport() && clientInsideCollider(state, world);
        FloatVector start = state.position();
        // fluid current, before the travel of any medium
        FluidCurrent.apply(state, world, state.boundingBox());

        PredictionEngine engine = engineFor(state, world, correctionThreshold);
        SimulationResult result = engine.run();
        state.inWater(engine instanceof WaterPredictionEngine);
        // an overlap the client claims suspends the tick only while it moves no farther than the
        // simulation; the simulated velocity is kept so a declared delta cannot feed the next tick
        if (clientInsideCollider && state.client().position().subtract(start).length()
                <= state.position().subtract(start).length()) {
            FloatVector velocity = state.velocity();
            state.resetToClient();
            state.velocity(velocity);
            return result(state, false);
        }
        return result;
    }

    /**
     * Eye cell water test: liquid height (y + 1) - (depth / 9 - 1 / 9) of that cell alone, against
     * the eye height of the pose (0.4 horizontal, 1.27001 sneaking, 1.62001 standing).
     */
    static boolean headInWater(AuthoritativeMotionState state, MovementWorldView world) {
        float eye = state.swimming() || state.gliding() ? 0.4f
                : state.sneaking() ? 1.62001f - 0.35f : 1.62001f;
        FloatVector position = state.position();
        float eyeY = position.y() + eye;
        int y = (int) Math.floor(eyeY);
        MovementBlockView cell = world.block((int) Math.floor(position.x()), y,
                (int) Math.floor(position.z()));
        if (cell.fluid() != 1) return false;
        int depth = cell.fluidDepth() < 8 ? cell.fluidDepth() + 1 : 1;
        float drop = (float) depth / 9.0f + Float.intBitsToFloat(0xbde38e39);
        float height = (float) (y + 1) - drop;
        return height > eyeY;
    }

    private PredictionEngine engineFor(AuthoritativeMotionState state, MovementWorldView world,
                                       float correctionThreshold) {
        FluidState fluid = world.fluidState(state.boundingBox());
        // the fluid material picks lava before water
        if (fluid.lava()) {
            return new LavaPredictionEngine(state, world, collisions, correctionThreshold, fluid);
        }
        if (fluid.water()) {
            return new WaterPredictionEngine(state, world, collisions, correctionThreshold, fluid);
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
                // temp until i fix that shi
                || world.hasBambooNearby(state.boundingBox())) {
            return false;
        }
        return !state.flying() && !state.justDisabledFlight() && !state.noClip()
                && state.alive() && state.supportedGameMode();
    }

    private static boolean clientInsideCollider(AuthoritativeMotionState state,
                                                MovementWorldView world) {
        return !MovementCollisionEngine.solid(world.collisionBoxes(state.clientBoundingBox()), world,
                state.clientBoundingBox().minY(), false).isEmpty();
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
        float multiplier = PredictionEngine.jumpPreventionMultiplier(state, world);
        float height = state.jumpHeight() * multiplier;
        float y = height > velocity.y() ? height : velocity.y();
        float z = velocity.z();
        state.jumpDelay(MovementConstants.JUMP_DELAY_TICKS);
        if (state.sprinting()) {
            float coordinate = (state.rotation().z() * 0.017453292f) * 10_430.378f;
            x = x - MovementConstants.sineEntry(coordinate) * 0.2f;
            z = z + MovementConstants.sineEntry(coordinate + 16_384.0f) * 0.2f;
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
