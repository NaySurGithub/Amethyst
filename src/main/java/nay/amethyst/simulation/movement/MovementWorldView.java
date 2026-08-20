package nay.amethyst.simulation.movement;

import java.util.List;

public interface MovementWorldView {
    MovementBlockView block(int x, int y, int z);

    List<FloatBox> collisionBoxes(FloatBox area);

    boolean contains(FloatBox area);

    boolean hasLiquidIntersection(FloatBox area);

    FluidState fluidState(FloatBox area);

    /** Swim acceleration for this frame, which is what Depth Strider and Dolphin's Grace change. */
    float underwaterSpeed();

    boolean hasBambooNearby(FloatBox area);

    boolean hasScaffoldingIntersection(FloatBox area);

    /** A piston is mid-stroke here, so the player is being displaced by the server, not by input. */
    boolean hasMovingBlock(FloatBox area);

    /** A boat or a minecart is close enough to carry or block the player, and it moves on its own. */
    boolean hasSolidEntityNearby(FloatBox area);


    MovementBlockPosition supportingBlock(FloatBox area, FloatVector playerPosition);
}
