package nay.amethyst.simulation.movement;

/** One tick of movement in a single medium. */
public abstract class PredictionEngine {

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

    protected void move() {
        collisions.move(state, world, correctionThreshold);
    }

    protected void applyKnockback() {
        if (state.hasKnockback()) {
            state.velocity(state.knockback());
        }
        if (state.riptideActive()
                && world.hasSolidEntityIntersecting(state.boundingBox().grow(1.0f, 1.0f, 1.0f))) {
            state.velocity(state.velocity().multiply(RiptidePhysics.ENTITY_IMPACT_MULTIPLIER));
            state.fallDistance(0.0f);
            state.stopRiptide();
        }
    }

    protected void moveRelative(float speed) {
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

    protected void applyFluidPush(FluidState fluid, float speed) {
        FloatVector flow = fluid.flow();
        if (flow.lengthSquared() <= 0.0f) {
            return;
        }
        float length = flow.length();
        state.velocity(state.velocity().add(flow.x() / length * speed,
                flow.y() / length * speed, flow.z() / length * speed));
    }

    protected boolean insideBlockNamed(String name) {
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

    /** Whether the volume above the player is clear of both blocks and fluid. */
    protected boolean canClimbOut(float boxBottom) {
        FloatVector velocity = state.velocity();
        FloatBox probe = state.boundingBox().offset(new FloatVector(velocity.x(),
                velocity.y() + 0.6f - state.boundingBox().minY() + boxBottom, velocity.z()));
        if (!world.collisionBoxes(probe).isEmpty()) {
            return false;
        }
        FluidState probed = world.fluidState(probe);
        return !probed.water() && !probed.lava();
    }

    protected MovementBlockView blockUnder(float distance) {
        FloatVector position = state.position().add(0.0f, -distance, 0.0f);
        return world.block(floor(position.x()), floor(position.y()), floor(position.z()));
    }

    protected float jumpPreventionMultiplier() {
        if (!state.onGround()) {
            return 1.0f;
        }
        FloatBox box = state.boundingBox();
        int x = floor(state.position().x());
        int z = floor(state.position().z());
        int feetY = floor(box.minY());
        return world.block(x, feetY, z).preventsJumping()
                || world.block(x, feetY - 1, z).preventsJumping()
                ? MovementConstants.PREVENTED_JUMP_MULTIPLIER : 1.0f;
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
}
