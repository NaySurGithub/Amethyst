package nay.amethyst.history.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The positions the movement checks ask about by category. Answering those questions by walking the
 * whole block map costs a full scan several times per tick, while the categories are nearly always
 * empty, so they are collected once when the frame is captured.
 */
public record BlockIndex(
        List<BlockPos> fluids,
        List<BlockPos> bamboo,
        List<BlockPos> scaffolding,
        List<BlockPos> moving,
        List<BlockPos> collidable
) {
    public static final BlockIndex EMPTY = new BlockIndex(List.of(), List.of(), List.of(),
            List.of(), List.of());

    public BlockIndex {
        fluids = List.copyOf(fluids);
        bamboo = List.copyOf(bamboo);
        scaffolding = List.copyOf(scaffolding);
        moving = List.copyOf(moving);
        collidable = List.copyOf(collidable);
    }

    public static BlockIndex of(Map<BlockPos, BlockFrame> blocks) {
        List<BlockPos> fluids = new ArrayList<>();
        List<BlockPos> bamboo = new ArrayList<>();
        List<BlockPos> scaffolding = new ArrayList<>();
        List<BlockPos> moving = new ArrayList<>();
        List<BlockPos> collidable = new ArrayList<>();

        for (Map.Entry<BlockPos, BlockFrame> entry : blocks.entrySet()) {
            BlockPos position = entry.getKey();
            BlockFrame block = entry.getValue();
            if (block.water() || block.lava()) {
                fluids.add(position);
            }
            if (block.id().equals("minecraft:bamboo")) {
                bamboo.add(position);
            }
            if (block.id().equals("minecraft:scaffolding")) {
                scaffolding.add(position);
            }
            if (block.id().contains("moving_block") || block.id().contains("piston_arm")) {
                moving.add(position);
            }
            if (!block.collisions().isEmpty()) {
                collidable.add(position);
            }
        }

        return new BlockIndex(fluids, bamboo, scaffolding, moving, collidable);
    }
}
