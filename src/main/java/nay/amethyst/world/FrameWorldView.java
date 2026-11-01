package nay.amethyst.world;

import nay.amethyst.simulation.movement.AuthoritativeMotionState;
import nay.amethyst.simulation.movement.FloatBox;
import nay.amethyst.simulation.movement.FloatVector;
import nay.amethyst.simulation.movement.FluidState;
import nay.amethyst.simulation.movement.MovementBlockPosition;
import nay.amethyst.simulation.movement.MovementBlockView;
import nay.amethyst.simulation.movement.MovementSimulator;
import nay.amethyst.simulation.movement.MovementWorldView;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class FrameWorldView implements MovementWorldView {
    private static final float SOLID_ENTITY_RANGE = 1.5f;


    private static final String POWDER_SNOW = "minecraft:powder_snow";

    private final WorldFrame frame;
    private final AuthoritativeMotionState state;
    private final Map<BlockFrame, MovementBlockView> views = new IdentityHashMap<>();

    private FloatBox fluidBox;
    private FluidState fluidResult;

    public FrameWorldView(WorldFrame frame, AuthoritativeMotionState state) {
        this.frame = frame;
        this.state = state;
    }

    /**
     * BDS powder snow support height: none below the top (2^-23 tolerance) or while descending
     * through it, 0.9 after a fall longer than 2.5, else a full block for leather boots only.
     */
    static float powderSnowSupportHeight(AuthoritativeMotionState state, float blockTop) {
        boolean fallThrough = state.sneaking() && state.wearingLeatherBoots();
        if (state.position().y() < blockTop - 0.00000011920929f || fallThrough) return 0.0f;
        if (!(state.fallDistance() <= 2.5f)) return 0.9f;
        return state.wearingLeatherBoots() ? 1.0f : 0.0f;
    }

    /** The powder snow box this actor collides with, or null. */
    private Aabb powderSnowBox(Aabb box) {
        BlockFrame block = frame.blockAt((int) Math.floor(box.minX()), (int) Math.floor(box.minY()),
                (int) Math.floor(box.minZ()));
        if (block == null || !POWDER_SNOW.equals(block.id())) return box;
        float bottom = (float) Math.floor(box.minY());
        float height = powderSnowSupportHeight(state, bottom + 1.0f);
        if (height <= 0.0f) return null;
        return new Aabb(box.minX(), bottom, box.minZ(), box.maxX(), bottom + height, box.maxZ());
    }

    private boolean ignoredPowderSnow(Aabb box) {
        return powderSnowBox(box) == null;
    }

    @Override
    public MovementBlockView block(int x, int y, int z) {
        BlockFrame block = frame.blockAt(x, y, z);
        if (block == null) {
            return MovementBlockView.AIR;
        }
        return views.computeIfAbsent(block, FrameWorldView::view);
    }

    private static MovementBlockView view(BlockFrame source) {
        List<Aabb> boxes = source.collisions();
        boolean fullCube = boxes.size() == 1 && boxes.get(0).maxX() - boxes.get(0).minX() >= 1.0
                && boxes.get(0).maxY() - boxes.get(0).minY() >= 1.0
                && boxes.get(0).maxZ() - boxes.get(0).minZ() >= 1.0;
        boolean solidMotion = !boxes.isEmpty() && !source.water() && !source.lava()
                && !source.climbable() && !source.id().contains("web");
        return new MovementBlockView(source.id(), (float) source.friction(),
                source.water() || source.lava(), source.id().equals("minecraft:bamboo"),
                source.climbable(), source.lava() ? 2 : source.water() ? 1 : 0,
                source.fluidDepth(), source.scaffoldingStability(), source.bubbleDirection(),
                solidMotion, fullCube, boxes.isEmpty());
    }

    @Override
    public List<FloatBox> collisionBoxes(FloatBox area) {
        Aabb query = toAabb(area);
        List<Aabb> boxes = frame.collisionBoxes(query);
        List<FloatBox> converted = new ArrayList<>(boxes.size());
        for (Aabb box : boxes) {
            Aabb kept = powderSnowBox(box);
            if (kept == null) {
                continue;
            }
            converted.add(toFloatBox(kept));
        }

        // whole entity boxes after every block shape, kept when they overlap the query grown by 0.25
        Aabb grown = toAabb(area.grow(0.25f, 0.25f, 0.25f));
        for (Aabb box : frame.solidEntityBoxes()) {
            if (box.intersects(grown)) {
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
    public float lavaSpeed() {
        float speed = (float) frame.physics().lavaMovementSpeed();
        return speed > 0.0f ? speed : 0.02f;
    }

    @Override
    public int mobHitCount(FloatBox area) {
        Aabb probe = toAabb(area);
        int hits = 0;
        for (Aabb box : frame.solidEntityBoxes()) {
            if (box.intersects(probe)) hits++;
        }
        return hits;
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

    private static int floor(float value) {
        int truncated = (int) value;
        return value < truncated ? truncated - 1 : truncated;
    }

    private FluidState computeFluidState(FloatBox area) {
        // BDS liquid query bounds: water contracted by 0.001 / 0.401, lava by 0.1 / 0.4
        float[] water = contract(area, 0.001f, 0.401f);
        float[] lava = contract(area, 0.1f, 0.4f);
        boolean inWater = false;
        boolean inLava = false;
        float submersion = 0.0f;
        for (BlockPos position : frame.index().fluids()) {
            BlockFrame block = frame.blocks().get(position);
            if (block == null) {
                continue;
            }
            boolean waterHit = block.water() && !block.lava() && touches(water, position);
            boolean lavaHit = block.lava() && touches(lava, position);
            if (!waterHit && !lavaHit) continue;
            inWater |= waterHit;
            inLava |= lavaHit;
            submersion = Math.max(submersion, position.y() + (float) block.fluidHeight() - area.minY());
        }
        if (!inWater && !inLava) {
            return FluidState.NONE;
        }
        return new FluidState(inWater, inLava, submersion);
    }

    private static float[] contract(FloatBox box, float horizontal, float vertical) {
        float[] source = {box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()};
        float[] out = new float[6];
        for (int axis = 0; axis < 3; axis++) {
            float amount = axis == 1 ? vertical : horizontal;
            out[axis] = source[axis] + amount;
            out[axis + 3] = source[axis + 3] - amount;
            if (out[axis] > out[axis + 3]) {
                float middle = (source[axis] + source[axis + 3]) * 0.5f;
                out[axis] = middle;
                out[axis + 3] = middle;
            }
        }
        return out;
    }

    /** CollectLiquidCells: cells floor(min) .. floor(max + 1) - 1 whose corner lies within the bounds. */
    private static boolean touches(float[] bounds, BlockPos position) {
        int[] cell = {position.x(), position.y(), position.z()};
        for (int axis = 0; axis < 3; axis++) {
            int begin = floor(bounds[axis]);
            int end = floor(bounds[axis + 3] + 1.0f);
            if (cell[axis] < begin || cell[axis] >= end) return false;
            if (!((float) cell[axis] <= bounds[axis + 3])) return false;
        }
        return true;
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

    @Override
    public boolean hasSolidEntityIntersecting(FloatBox area) {
        List<Aabb> boxes = frame.solidEntityBoxes();
        if (boxes.isEmpty()) return false;
        Aabb probe = toAabb(area);
        for (Aabb box : boxes) {
            if (box.intersects(probe)) return true;
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
                if (ignoredPowderSnow(collision)) {
                    continue;
                }
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
