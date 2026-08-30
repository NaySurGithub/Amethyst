package nay.amethyst.simulation.movement;

import java.util.List;

public interface MovementWorldView {
    MovementBlockView block(int x, int y, int z);

    List<FloatBox> collisionBoxes(FloatBox area);

    boolean contains(FloatBox area);

    boolean hasLiquidIntersection(FloatBox area);

    FluidState fluidState(FloatBox area);

    /** Swim acceleration for this frame. */
    float underwaterSpeed();

    boolean hasBambooNearby(FloatBox area);

    boolean hasScaffoldingIntersection(FloatBox area);

    /** Whether a piston is mid-stroke in the area. */
    boolean hasMovingBlock(FloatBox area);

    /** Whether a boat or minecart is close enough to carry or block the player. */
    boolean hasSolidEntityNearby(FloatBox area);

    /** Whether a solid entity intersects this exact area. */
    boolean hasSolidEntityIntersecting(FloatBox area);

    MovementBlockPosition supportingBlock(FloatBox area, FloatVector playerPosition);
}
