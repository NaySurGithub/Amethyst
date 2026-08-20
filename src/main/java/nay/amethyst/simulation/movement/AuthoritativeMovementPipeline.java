package nay.amethyst.simulation.movement;

public final class AuthoritativeMovementPipeline {
    /** Past this the two are not describing the same movement, so the velocity is stale as well. */
    private static final float DESYNC_OFFSET = 8.0f;

    private boolean impulseDeferred;

    private final AuthoritativeMotionState state;
    private final MovementOptions options;
    private final MovementSimulator simulator;

    public AuthoritativeMovementPipeline(AuthoritativeMotionState state,
                                         MovementOptions options) {
        this.state = state;
        this.options = options;
        this.simulator = new MovementSimulator(options.simulateWater());
    }

    public synchronized MovementPipelineResult handle(MovementInputFrame input,
                                                      MovementWorldView world) {
        FloatVector clientFeet = input.position().add(0.0f,
                -MovementConstants.PLAYER_HEIGHT_OFFSET, 0.0f);
        if (!state.initialized()) {
            state.initialize(clientFeet, state.onGround());
        }

        boolean hadTeleport = state.hasTeleport();
        boolean hadKnockback = state.hasKnockback();
        boolean rawJumpGrace = state.claimRawJumpGrace(
                input.has(MovementInputFlag.JUMP_PRESSED_RAW));

        state.tickEffects();
        state.updateInput(input);
        impulseDeferred = false;
        MovementSimulator.SimulationResult simulation = simulateBestBranch(world, hadTeleport,
                hadKnockback);

        // kept before the re-anchor below, which would otherwise report the client's own position
        // back as the prediction and make every alert unreadable
        FloatVector predictedPosition = state.position();
        FloatVector positionDifference = state.position().subtract(state.client().position());
        FloatVector velocityDifference = state.velocity().subtract(state.client().velocity());
        boolean needsCorrection = positionDifference.length() > options.correctionThreshold();
        boolean correctionRequired = simulation.reliable()
                && !simulation.inFluid()
                && needsCorrection
                && state.pendingTeleports() == 0
                && !hadTeleport
                && !rawJumpGrace
                && !hadKnockback;

        // The offset has to mean "how far this tick's move missed", not "how far the server has
        // drifted since the player logged in". Without re-anchoring, one unexplained tick stays in
        // the position for hundreds of ticks and every one of them is counted again as a fresh
        // failure. The simulation restarts from where the client says it is, so the next tick
        // measures that tick alone; a cheat still has to explain a residual on every single tick.
        boolean anchored = simulation.reliable() && !hadTeleport && !hadKnockback
                && state.pendingTeleports() == 0 && state.pendingCorrections() == 0
                && !state.immobile();
        if (anchored) {
            state.position(state.client().position());
        }

        // Only an impulse the simulation could not know about justifies taking the client's velocity.
        // Doing it whenever the prediction missed turns the simulation into a mirror of the client:
        // it would adopt a cheat's own velocity and then predict the cheat correctly.
        if (hadTeleport || hadKnockback || impulseDeferred
                || positionDifference.length() > DESYNC_OFFSET) {
            state.velocity(state.client().velocity());
        }

        if (!correctionRequired && !needsCorrection && !hadTeleport && !hadKnockback
                && state.pendingCorrections() == 0) {
            boolean wasCoolingDown = state.correctionCooldown();
            state.correctionCooldown(false);
            boolean serverInsideBlocks = !world.collisionBoxes(state.boundingBox()).isEmpty();
            boolean clientInsideBlocks = !world.collisionBoxes(state.clientBoundingBox()).isEmpty();
            if (!wasCoolingDown && state.pendingTeleports() == 0 && !state.immobile()
                    && serverInsideBlocks == clientInsideBlocks
                    && options.acceptClientVelocity()
                    && velocityDifference.length() < options.velocityAcceptanceThreshold()) {
                state.velocity(state.client().velocity());
            }
        }

        FloatVector forwardedFeet = state.pendingTeleports() > 0
                ? state.pendingTeleportPosition() : state.position();
        FloatVector forwarded = forwardedFeet.add(0.0f,
                MovementConstants.CORRECTION_HEIGHT_OFFSET, 0.0f);
        state.finishInput();

        return new MovementPipelineResult(state.client().position(), predictedPosition,
                state.velocity(), forwarded, positionDifference, velocityDifference,
                correctionRequired, simulation.reliable(), simulation.inFluid(),
                hadKnockback && !impulseDeferred, impulseDeferred, anchored,
                describeSupport(world), state.supportingBlockY(), state.onGround(), input.tick());
    }

    /**
     * The client tells us a jump or a sprint started, but the flag and the tick it applies to do not
     * always line up: a jump can be reported the tick before the client actually leaves the ground,
     * and a sprint can be dropped or held one tick longer than the server believes. Simulating only
     * the literal reading of the input makes the server miss a real 0.42 jump, which shows up as a
     * large offset on a completely legitimate move.
     *
     * <p>So the ambiguous ticks are simulated both ways and the branch landing closest to the client
     * wins. A cheat gains nothing: every branch is still a legal move, so the residual offset it has
     * to explain is unchanged.
     */
    private MovementSimulator.SimulationResult simulateBestBranch(MovementWorldView world,
                                                                  boolean hadTeleport,
                                                                  boolean hadKnockback) {
        AuthoritativeMotionState.MotionSnapshot start = state.snapshot();
        MovementSimulator.SimulationResult result = simulator.simulate(state, world,
                options.correctionThreshold());
        if (hadTeleport) {
            return result;
        }

        float bestOffset = offsetFromClient();
        AuthoritativeMotionState.MotionSnapshot best = state.snapshot();

        // an impulse spent a tick before the client applied it moves the simulation a whole impulse
        // ahead, so the tick is also tried without it and the impulse stays armed if that fits better
        if (hadKnockback) {
            state.restore(start);
            state.deferKnockback();
            MovementSimulator.SimulationResult deferred = simulator.simulate(state, world,
                    options.correctionThreshold());
            float deferredOffset = offsetFromClient();
            if (deferredOffset < bestOffset) {
                bestOffset = deferredOffset;
                best = state.snapshot();
                result = deferred;
                impulseDeferred = true;
            }
        }

        // A branch is only worth simulating where flipping the input could change the outcome. A jump
        // needs ground and no cooldown to do anything, and sprint only feeds the movement input and
        // the jump boost, so with neither of those the two branches are identical by construction.
        boolean couldJump = start.onGround() && start.jumpDelay() == 0;
        boolean hasMovementInput = state.impulseForward() * state.impulseForward()
                + state.impulseSideways() * state.impulseSideways() >= 1.0E-4f;

        boolean jumpAmbiguous = couldJump
                && state.jumping() != (state.pressingJump() && start.onGround());
        boolean sprintAmbiguous = (hasMovementInput || couldJump && state.jumping())
                && state.sprinting() != state.pressingSprint();
        if (!jumpAmbiguous && !sprintAmbiguous) {
            state.restore(best);
            return result;
        }

        for (int branch = 1; branch < 4; branch++) {
            boolean flipJump = (branch & 1) != 0;
            boolean flipSprint = (branch & 2) != 0;
            if (flipJump && !jumpAmbiguous || flipSprint && !sprintAmbiguous) {
                continue;
            }

            state.restore(start);
            if (impulseDeferred) {
                state.deferKnockback();
            }
            if (flipJump) {
                state.jumping(!start.jumping());
            }
            if (flipSprint) {
                state.sprinting(!start.sprinting());
            }

            MovementSimulator.SimulationResult candidate = simulator.simulate(state, world,
                    options.correctionThreshold());
            float offset = offsetFromClient();
            if (offset < bestOffset) {
                bestOffset = offset;
                best = state.snapshot();
                result = candidate;
            }
        }

        state.restore(best);
        return result;
    }

    /** Returns the block id itself, so nothing is formatted on a tick that never raises an alert. */
    private String describeSupport(MovementWorldView world) {
        if (!state.hasSupportingBlock()) {
            return "none";
        }
        return world.block(state.supportingBlockX(), state.supportingBlockY(),
                state.supportingBlockZ()).id();
    }

    private float offsetFromClient() {
        return state.position().subtract(state.client().position()).length();
    }

    public AuthoritativeMotionState state() {
        return state;
    }
}
