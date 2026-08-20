package nay.amethyst.simulation.movement;

public final class ClientMotionState {
    private FloatVector position = FloatVector.ZERO;
    private FloatVector lastPosition = FloatVector.ZERO;
    private FloatVector velocity = FloatVector.ZERO;
    private FloatVector lastVelocity = FloatVector.ZERO;
    private FloatVector movement = FloatVector.ZERO;
    private FloatVector lastMovement = FloatVector.ZERO;
    private boolean toggledFlight;
    private boolean horizontalCollision;
    private boolean verticalCollision;

    public void update(MovementInputFrame input) {
        horizontalCollision = input.has(MovementInputFlag.HORIZONTAL_COLLISION);
        verticalCollision = input.has(MovementInputFlag.VERTICAL_COLLISION);
        lastPosition = position;
        position = input.position().add(0.0f, -MovementConstants.PLAYER_HEIGHT_OFFSET, 0.0f);
        lastVelocity = velocity;
        velocity = input.delta();
        lastMovement = movement;
        movement = position.subtract(lastPosition);
    }

    public void reset(FloatVector position) {
        this.position = position;
        lastPosition = position;
        velocity = FloatVector.ZERO;
        lastVelocity = FloatVector.ZERO;
        movement = FloatVector.ZERO;
        lastMovement = FloatVector.ZERO;
        toggledFlight = false;
        horizontalCollision = false;
        verticalCollision = false;
    }

    public FloatVector position() {
        return position;
    }

    public FloatVector lastPosition() {
        return lastPosition;
    }

    public FloatVector velocity() {
        return velocity;
    }

    public FloatVector lastVelocity() {
        return lastVelocity;
    }

    public FloatVector movement() {
        return movement;
    }

    public FloatVector lastMovement() {
        return lastMovement;
    }

    public boolean toggledFlight() {
        return toggledFlight;
    }

    public void toggledFlight(boolean toggledFlight) {
        this.toggledFlight = toggledFlight;
    }

    public boolean horizontalCollision() {
        return horizontalCollision;
    }

    public boolean verticalCollision() {
        return verticalCollision;
    }
}
