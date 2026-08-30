package nay.amethyst.simulation.movement;

public final class AuthoritativeMovementPipeline {
    private static final float DESYNC_OFFSET = 8.0f;

    private boolean impulseDeferred;

    private final AuthoritativeMotionState state;
    private final MovementOptions options;
    private final MovementSimulator simulator;

    public AuthoritativeMovementPipeline(AuthoritativeMotionState state,
                                         MovementOptions options) {
        this.state = state;
        this.options = options;
        this.simulator = new MovementSimulator();
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
        boolean wasGliding = state.gliding();
        impulseDeferred = false;
        MovementSimulator.SimulationResult simulation = simulateBestBranch(world, hadTeleport,
                hadKnockback);
        boolean inFluidTransition = simulation.inFluid()
                || world.fluidState(state.clientBoundingBox()).any();

        FloatVector predictedPosition = state.position();
        FloatVector positionDifference = state.position().subtract(state.client().position());
        FloatVector velocityDifference = state.velocity().subtract(state.client().velocity());
        boolean needsCorrection = positionDifference.length() > options.correctionThreshold();
        boolean correctionRequired = simulation.reliable()
                && !inFluidTransition
                && !wasGliding
                && needsCorrection
                && state.pendingTeleports() == 0
                && !hadTeleport
                && !rawJumpGrace
                && !hadKnockback;

        boolean anchored = simulation.reliable() && !hadTeleport && !hadKnockback
                && state.pendingTeleports() == 0 && state.pendingCorrections() == 0
                && !state.immobile();
        if (anchored) {
            state.position(state.client().position());
        }

        if (hadTeleport || hadKnockback || impulseDeferred
                || wasGliding && anchored
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
                correctionRequired, simulation.reliable(), inFluidTransition,
                hadKnockback && !impulseDeferred, impulseDeferred, state.ticksSinceKnockback(), anchored,
                describeSupport(world), state.supportingBlockY(), describeBelow(world),
                state.onGround(), input.tick());
    }

    /**
     * Simulates the ambiguous jump and sprint branches and keeps the one closest to the client.
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

        if (hadKnockback) {
            state.restore(start);
            if (!state.deferKnockback()) {
                state.restore(best);
                return result;
            }
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

        boolean clientGround = start.onGround() || state.client().verticalCollision();
        boolean couldJump = clientGround && start.jumpDelay() == 0;
        boolean hasMovementInput = state.impulseForward() * state.impulseForward()
                + state.impulseSideways() * state.impulseSideways() >= 1.0E-4f;

        boolean jumpAmbiguous = couldJump
                && state.jumping() != (state.pressingJump() && clientGround);
        boolean sprintAmbiguous = couldJump && state.jumping()
                || hasMovementInput && state.sprinting() != state.pressingSprint();
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
                boolean jumping = !start.jumping();
                state.jumping(jumping);
                if (jumping && !start.onGround()) {
                    state.onGround(true);
                }
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

    /** Id of the block the simulation believes is holding the player up. */
    private String describeSupport(MovementWorldView world) {
        if (!state.hasSupportingBlock()) {
            return "none";
        }
        return world.block(state.supportingBlockX(), state.supportingBlockY(),
                state.supportingBlockZ()).id();
    }

    /** Names the block under the client and how much collision the frame kept for it. */
    private String describeBelow(MovementWorldView world) {
        FloatVector feet = state.client().position();
        int x = (int) Math.floor(feet.x());
        int y = (int) Math.floor(feet.y() - 0.1f);
        int z = (int) Math.floor(feet.z());
        FloatBox cell = new FloatBox(x, y, z, x + 1.0f, y + 1.0f, z + 1.0f);
        String below = world.block(x, y, z).id() + "@" + y + "/" + world.collisionBoxes(cell).size();
        if (state.wearingElytra() || state.gliding()) {
            below += " glide=" + state.gliding() + "/" + state.wearingElytra();
        }
        FluidState fluid = world.fluidState(state.clientBoundingBox());
        if (!fluid.water() && !fluid.lava()) {
            return below;
        }
        return below + " sub=" + String.format("%.3f", fluid.submersion());
    }

    private float offsetFromClient() {
        return state.position().subtract(state.client().position()).length();
    }

    public AuthoritativeMotionState state() {
        return state;
    }
}
