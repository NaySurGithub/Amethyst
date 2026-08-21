package nay.amethyst.history.model;

import nay.amethyst.prediction.common.Vec3;
import org.powernukkitx.block.Block;
import org.powernukkitx.block.BlockBubbleColumn;
import org.powernukkitx.block.BlockFenceGate;
import org.powernukkitx.block.BlockLiquid;
import org.powernukkitx.block.BlockStairs;
import org.powernukkitx.math.AxisAlignedBB;

import java.util.ArrayList;
import java.util.List;

public record BlockFrame(
        String id,
        double friction,
        boolean water,
        boolean lava,
        boolean climbable,
        double fluidHeight,
        Vec3 flow,
        int bubbleDirection,
        List<Aabb> collisions,
        StairFrame stair
) {
    private static final float MUD_SINK = 0.125f;

    public BlockFrame {
        collisions = List.copyOf(collisions);
    }

    private static final BlockFrame AIR = new BlockFrame("minecraft:air", 0.6, false, false, false,
            0, Vec3.ZERO, 0, List.of(), null);

    public static BlockFrame capture(Block block) {
        return capture(block, false);
    }

    public static BlockFrame capture(Block block, boolean walksOnPowderSnow) {
        if (block.isAir()) {
            return AIR;
        }

        String blockId = block.getId();
        boolean powderSnow = blockId.contains("powder_snow");
        float topInset = blockId.equals("minecraft:mud") ? MUD_SINK : 0.0f;

        List<Aabb> collisions = new ArrayList<>();
        AxisAlignedBB[] boxes = block.isAir() || powderSnow && !walksOnPowderSnow
                ? null : block.getCollisionBoxes();
        if (!(block instanceof BlockFenceGate gate && gate.isOpen()) && boxes != null) {
            for (AxisAlignedBB box : boxes) {
                if (box == null) continue;
                Aabb collision = Aabb.from(box);
                collisions.add(new Aabb((float) collision.minX(), (float) collision.minY(),
                        (float) collision.minZ(), (float) collision.maxX(),
                        (float) collision.maxY() - topInset, (float) collision.maxZ()));
            }
        }
        boolean water = block.getId().contains("water") || block instanceof BlockBubbleColumn;
        boolean lava = block.getId().contains("lava");
        double fluidHeight = 0;
        Vec3 flow = Vec3.ZERO;
        if (block instanceof BlockLiquid liquid) {
            fluidHeight = 1.0 - liquid.getFluidHeightPercent();
            var vector = liquid.getFlowVector();
            flow = new Vec3(vector.x, vector.y, vector.z);
        } else if (block instanceof BlockBubbleColumn) {
            fluidHeight = 1;
        }
        int bubble = block instanceof BlockBubbleColumn column ? (column.isDragDown() ? -1 : 1) : 0;
        StairFrame stair = block instanceof BlockStairs stairs
                ? new StairFrame(stairs.getBlockFace(), stairs.isUpsideDown()) : null;
        return new BlockFrame(block.getId(), movementFriction(block), water, lava,
                block.canBeClimbed(), fluidHeight, flow, bubble, collisions, stair);
    }

    public static double movementFriction(Block block) {
        String id = block.getId();
        if (id.contains("blue_ice")) return 0.989;
        if (id.contains("ice")) return 0.98;
        if (id.contains("slime")) return 0.8;
        return 0.6;
    }

    public static BlockFrame combine(BlockFrame primary, BlockFrame extra) {
        if (extra == null || (!extra.water && !extra.lava && extra.bubbleDirection == 0)) return primary;
        if (primary == null) return extra;
        return new BlockFrame(primary.id, primary.friction, primary.water || extra.water,
                primary.lava || extra.lava, primary.climbable || extra.climbable,
                Math.max(primary.fluidHeight, extra.fluidHeight),
                extra.flow.lengthSquared() > 0 ? extra.flow : primary.flow,
                extra.bubbleDirection != 0 ? extra.bubbleDirection : primary.bubbleDirection,
                primary.collisions, primary.stair);
    }

    public BlockFrame withCollisions(List<Aabb> collisions) {
        return new BlockFrame(id, friction, water, lava, climbable, fluidHeight, flow,
                bubbleDirection, collisions, stair);
    }

    public boolean relevant() {
        return !collisions.isEmpty() || water || lava || climbable || fluidHeight > 0
                || bubbleDirection != 0 || Math.abs(friction - 0.6) > 1.0E-6
                || id.contains("web") || id.contains("sweet_berry_bush")
                || id.contains("honey_block") || id.contains("slime") || id.contains("bed")
                || id.contains("soul_sand") || id.contains("bamboo")
                || id.contains("scaffolding") || id.contains("moving_block")
                || id.contains("powder_snow");
    }
}
