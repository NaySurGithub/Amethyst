package nay.amethyst.simulation.movement;

import nay.amethyst.history.model.Aabb;
import nay.amethyst.history.model.BlockFrame;
import nay.amethyst.history.model.BlockPos;
import nay.amethyst.history.model.WorldFrame;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class FrameWorldView implements MovementWorldView {
    private static final float SOLID_ENTITY_RANGE = 1.5f;

    private final WorldFrame frame;
    private final Map<BlockFrame, MovementBlockView> views = new IdentityHashMap<>();

    private FloatBox fluidBox;
    private FluidState fluidResult;

    public FrameWorldView(WorldFrame frame) {
        this.frame = frame;
    }

    @Override
    public MovementBlockView block(int x, int y, int z) {
        BlockFrame block = frame.blockAt(x, y, z);
        if (block == null) {
            return MovementBlockView.AIR;
        }
        return views.computeIfAbsent(block, source -> new MovementBlockView(source.id(),
                (float) source.friction(), source.water() || source.lava(),
                source.id().equals("minecraft:bamboo"), source.climbable()));
    }

    @Override
    public List<FloatBox> collisionBoxes(FloatBox area) {
        Aabb query = toAabb(area);
        List<Aabb> boxes = frame.collisionBoxes(query);
        List<FloatBox> converted = new ArrayList<>(boxes.size());
        for (Aabb box : boxes) {
            converted.add(toFloatBox(box));
        }

        for (Aabb box : frame.solidEntityBoxes()) {
            if (box.intersects(query)) {
                converted.add(toFloatBox(box));
            }
        }
        return converted;
    }

    @Override
    public boolean contains(FloatBox area) {
        return frame.covers(toAabb(area));
    }

    @Override
    public boolean hasLiquidIntersection(FloatBox area) {
        for (BlockPos position : frame.index().fluids()) {
            if (intersectsCell(area, position)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public float underwaterSpeed() {
        float speed = (float) frame.physics().underwaterMovementSpeed();
        return speed > 0.0f ? speed : MovementSimulator.WATER_ACCELERATION;
    }

    @Override
    public FluidState fluidState(FloatBox area) {
        if (area.equals(fluidBox)) {
            return fluidResult;
        }

        FluidState state = computeFluidState(area);
        fluidBox = area;
        fluidResult = state;
        return state;
    }

    private FluidState computeFluidState(FloatBox area) {
        boolean water = false;
        boolean lava = false;
        float submersion = 0.0f;
        float flowX = 0.0f;
        float flowY = 0.0f;
        float flowZ = 0.0f;
        int bubbleDirection = 0;
        boolean bubbleSurface = false;

        for (BlockPos position : frame.index().fluids()) {
            BlockFrame block = frame.blocks().get(position);
            if (block == null) {
                continue;
            }

            float top = position.y() + (float) block.fluidHeight();
            if (top <= area.minY() || position.y() >= area.maxY()
                    || position.x() + 1 <= area.minX() || position.x() >= area.maxX()
                    || position.z() + 1 <= area.minZ() || position.z() >= area.maxZ()) {
                continue;
            }

            water |= block.water();
            lava |= block.lava();
            submersion = Math.max(submersion, top - area.minY());
            flowX += (float) block.flow().x();
            flowY += (float) block.flow().y();
            flowZ += (float) block.flow().z();

            if (block.bubbleDirection() != 0 && bubbleDirection == 0) {
                bubbleDirection = block.bubbleDirection();
                bubbleSurface = frame.blockAt(position.x(), position.y() + 1, position.z()) == null;
            }
        }

        if (!water && !lava) {
            return FluidState.NONE;
        }
        return new FluidState(water, lava, submersion, new FloatVector(flowX, flowY, flowZ),
                bubbleDirection, bubbleSurface);
    }

    @Override
    public boolean hasBambooNearby(FloatBox area) {
        FloatBox grown = area.grow(1.0f, 1.0f, 1.0f);
        for (BlockPos position : frame.index().bamboo()) {
            if (intersectsCell(grown, position)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean hasScaffoldingIntersection(FloatBox area) {
        for (BlockPos position : frame.index().scaffolding()) {
            if (intersectsCell(area, position)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean hasMovingBlock(FloatBox area) {
        FloatBox grown = area.grow(1.0f, 1.0f, 1.0f);
        for (BlockPos position : frame.index().moving()) {
            if (intersectsCell(grown, position)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean hasSolidEntityNearby(FloatBox area) {
        List<Aabb> boxes = frame.solidEntityBoxes();
        if (boxes.isEmpty()) {
            return false;
        }

        Aabb probe = toAabb(area.grow(SOLID_ENTITY_RANGE, SOLID_ENTITY_RANGE, SOLID_ENTITY_RANGE));
        for (int i = 0; i < boxes.size(); i++) {
            if (boxes.get(i).intersects(probe)) {
                return true;
            }
        }
        return false;
    }

    private static boolean intersectsCell(FloatBox area, BlockPos position) {
        return area.maxX() > position.x() && area.minX() < position.x() + 1.0f
                && area.maxY() > position.y() && area.minY() < position.y() + 1.0f
                && area.maxZ() > position.z() && area.minZ() < position.z() + 1.0f;
    }

    @Override
    public MovementBlockPosition supportingBlock(FloatBox area, FloatVector playerPosition) {
        int playerBlockX = (int) Math.floor(playerPosition.x());
        int playerBlockY = (int) Math.floor(playerPosition.y());
        int playerBlockZ = (int) Math.floor(playerPosition.z());
        float centerX = playerBlockX + 0.5f;
        float centerY = playerBlockY + 0.5f;
        float centerZ = playerBlockZ + 0.5f;
        MovementBlockPosition closest = null;
        float closestDistance = Float.MAX_VALUE - 1.0f;
        for (BlockPos position : frame.index().collidable()) {
            BlockFrame block = frame.blocks().get(position);
            if (block == null) {
                continue;
            }
            boolean intersects = false;
            for (Aabb collision : block.collisions()) {
                if (area.intersects(toFloatBox(collision))) {
                    intersects = true;
                    break;
                }
            }
            if (!intersects) {
                continue;
            }
            float x = position.x() - centerX;
            float y = position.y() - centerY;
            float z = position.z() - centerZ;
            float distance = x * x + y * y + z * z;
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = new MovementBlockPosition(position.x(), position.y(), position.z());
            }
        }
        return closest;
    }

    private static Aabb toAabb(FloatBox box) {
        return new Aabb(box.minX(), box.minY(), box.minZ(),
                box.maxX(), box.maxY(), box.maxZ());
    }

    private static FloatBox toFloatBox(Aabb box) {
        return new FloatBox((float) box.minX(), (float) box.minY(), (float) box.minZ(),
                (float) box.maxX(), (float) box.maxY(), (float) box.maxZ());
    }
}
