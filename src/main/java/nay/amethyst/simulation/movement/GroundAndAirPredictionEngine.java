package nay.amethyst.simulation.movement;

/** Walking, falling and jumping, driven by block friction. */
public final class GroundAndAirPredictionEngine extends PredictionEngine {

    public GroundAndAirPredictionEngine(AuthoritativeMotionState state, MovementWorldView world,
                                        MovementCollisionEngine collisions,
                                        float correctionThreshold) {
        super(state, world, collisions, correctionThreshold);
    }

    @Override
    public MovementSimulator.SimulationResult run() {
        MovementBlockView blockUnder = blockUnder(0.5f);
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

        applyKnockback();
        moveRelative(acceleration);
        applyJump();
        applyClimbable();

        boolean cobweb = insideBlockNamed("web");
        if (cobweb) {
            state.velocity(state.velocity().multiply(0.25f, 0.05f, 0.25f));
        } else if (insideBlockNamed("powder_snow")) {
            state.velocity(state.velocity().multiply(0.9f, 1.5f, 0.9f));
        } else if (insideBlockNamed("sweet_berry_bush")) {
            state.velocity(state.velocity().multiply(0.8f, 0.75f, 0.8f));
        }

        FloatVector oldVelocity = state.velocity();
        boolean oldOnGround = state.onGround();
        float oldY = state.position().y();
        move();

        if (state.hasSupportingBlock()) {
            blockUnder = world.block(state.supportingBlockX(), state.supportingBlockY(),
                    state.supportingBlockZ());
        } else {
            blockUnder = blockUnder(0.2f);
            if (blockUnder.air()) {
                MovementBlockView below = world.block(floor(state.position().x()),
                        floor(state.position().y()) - 1, floor(state.position().z()));
                if (below.id().contains("wall") || below.id().contains("fence")) {
                    blockUnder = below;
                }
            }
        }

        if (oldY == state.position().y()) {
            walkOnBlock(blockUnder);
        }
        state.movement(state.velocity());
        postCollisionMotion(oldVelocity, oldOnGround, blockUnder);

        if (!oldOnGround && state.onGround()) {
            state.jumpDelay(0);
        }

        if (cobweb) {
            state.velocity(FloatVector.ZERO);
        }

        if (recoverSupportedClientBranch()) {
            state.resetToClient();
            state.onGround(true);
            state.clearSupportingBlock();
            return result(false);
        }

        if (recoverDepartedEdge()) {
            state.resetToClient();
            state.onGround(false);
            state.clearSupportingBlock();
            return result(false);
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
        return result(true);
    }

    private void applyJump() {
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

    private void applyClimbable() {
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

    private void walkOnBlock(MovementBlockView block) {
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

    private void postCollisionMotion(FloatVector oldVelocity, boolean oldOnGround,
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

    private boolean recoverSupportedClientBranch() {
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

    private boolean recoverDepartedEdge() {
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

    static boolean hasSupport(MovementWorldView world, FloatBox box, float depth) {
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
}
