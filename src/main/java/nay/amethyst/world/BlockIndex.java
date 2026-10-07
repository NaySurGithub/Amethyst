package nay.amethyst.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Captured block positions grouped by the categories the movement checks query. */
public record BlockIndex(
        List<BlockPos> fluids,
        List<BlockPos> moving,
        List<BlockPos> collidable
) {
    public static final BlockIndex EMPTY = new BlockIndex(List.of(), List.of(), List.of());

    public BlockIndex {
        fluids = List.copyOf(fluids);
        moving = List.copyOf(moving);
        collidable = List.copyOf(collidable);
    }

    public static BlockIndex of(Map<BlockPos, BlockFrame> blocks) {
        List<BlockPos> fluids = new ArrayList<>();
        List<BlockPos> moving = new ArrayList<>();
        List<BlockPos> collidable = new ArrayList<>();

        for (Map.Entry<BlockPos, BlockFrame> entry : blocks.entrySet()) {
            BlockPos position = entry.getKey();
            BlockFrame block = entry.getValue();
            if (block.water() || block.lava()) {
                fluids.add(position);
            }
            if (block.id().contains("moving_block") || block.id().contains("piston_arm")) {
                moving.add(position);
            }
            if (!block.collisions().isEmpty()) {
                collidable.add(position);
            }
        }

        return new BlockIndex(fluids, moving, collidable);
    }
}
