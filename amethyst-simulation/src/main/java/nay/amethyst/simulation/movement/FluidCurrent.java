package nay.amethyst.simulation.movement;

import java.util.ArrayList;
import java.util.List;

/**
 * Fluid current push (Bedrock 1.26.52 / BDS): lava is tried first, then water. Every liquid cell
 * of the material inside the contracted query bounds contributes its normalised flow; the sum is
 * normalised and scaled by 0.014 (water) or 0.0035 (lava). A cell of unknown depth aborts.
 */
public final class FluidCurrent {
    private static final int[] DIRECTION_AXIS = {2, 0, 2, 0};
    private static final int[] DIRECTION_STEP = {-1, 1, 1, -1};
    private static final int UNKNOWN_DEPTH = 255;

    private FluidCurrent() {
    }

    public static void apply(AuthoritativeMotionState state, MovementWorldView world, FloatBox sensing) {
        for (int material = 2; material >= 1; material--) {
            float[] box = material == 1 ? contract(sensing, 0.001f, 0.401f)
                    : contract(sensing, 0.1f, 0.4f);
            int x0 = floor(box[0]);
            int y0 = floor(box[1]);
            int z0 = floor(box[2]);
            int x1 = floor(box[3] + 1.0f);
            int y1 = floor(box[4] + 1.0f);
            int z1 = floor(box[5] + 1.0f);
            boolean unknown = false;
            List<int[]> cells = new ArrayList<>();
            for (int y = y0; y < y1; y++) {
                for (int z = z0; z < z1; z++) {
                    for (int x = x0; x < x1; x++) {
                        MovementBlockView block = world.block(x, y, z);
                        if (block.fluid() != material) continue;
                        if (block.fluidDepth() == UNKNOWN_DEPTH) unknown = true;
                        cells.add(new int[]{x, y, z, depthOf(block)});
                    }
                }
            }
            if (cells.isEmpty()) continue;
            if (unknown) return;
            boolean admitted = false;
            for (int[] cell : cells) {
                if (cell[3] > 0) {
                    admitted = true;
                    break;
                }
            }
            if (!admitted) {
                for (int[] cell : cells) {
                    for (int d = 0; d < 4 && !admitted; d++) {
                        int axis = DIRECTION_AXIS[d];
                        int edge = axis == 0 ? (DIRECTION_STEP[d] < 0 ? x0 : x1 - 1)
                                : (DIRECTION_STEP[d] < 0 ? z0 : z1 - 1);
                        if (cell[axis == 0 ? 0 : 2] != edge) continue;
                        int[] n = {cell[0], cell[1], cell[2]};
                        n[axis] += DIRECTION_STEP[d];
                        MovementBlockView other = world.block(n[0], n[1], n[2]);
                        if (other.fluid() == material && other.fluidDepth() != UNKNOWN_DEPTH
                                && other.fluidDepth() != 0) {
                            admitted = true;
                        }
                    }
                }
            }
            if (!admitted) return;
            float sumX = 0.0f;
            float sumY = 0.0f;
            float sumZ = 0.0f;
            for (int[] cell : cells) {
                float[] flow = flowAt(world, cell[0], cell[1], cell[2], material);
                sumX += flow[0];
                sumY += flow[1];
                sumZ += flow[2];
            }
            float length = (float) Math.sqrt(sumX * sumX + sumY * sumY + sumZ * sumZ);
            if (length >= 0.0001f) {
                float k = material == 1 ? 0.014f : 0.0035f;
                FloatVector velocity = state.velocity();
                state.velocity(new FloatVector(velocity.x() + sumX / length * k,
                        velocity.y() + sumY / length * k, velocity.z() + sumZ / length * k));
            }
            return;
        }
    }

    private static int depthOf(MovementBlockView block) {
        return block.fluidDepth() == UNKNOWN_DEPTH ? 0 : block.fluidDepth();
    }

    private static int effectiveDepth(int depth) {
        return depth < 8 ? depth : 0;
    }

    static float[] flowAt(MovementWorldView world, int x, int y, int z, int material) {
        MovementBlockView cell = world.block(x, y, z);
        boolean same = cell.fluid() == material;
        int depth = same ? effectiveDepth(depthOf(cell)) : -1;
        float[] f = {0.0f, 0.0f, 0.0f};
        for (int d = 0; d < 4; d++) {
            int[] n = {x, y, z};
            n[DIRECTION_AXIS[d]] += DIRECTION_STEP[d];
            MovementBlockView other = world.block(n[0], n[1], n[2]);
            if (other.fluid() == material) {
                f[DIRECTION_AXIS[d]] += (float) ((effectiveDepth(depthOf(other)) - depth) * DIRECTION_STEP[d]);
                continue;
            }
            if (other.solidMotion()) continue;
            MovementBlockView below = world.block(n[0], n[1] - 1, n[2]);
            if (below.fluid() == material) {
                f[DIRECTION_AXIS[d]] += (float) ((effectiveDepth(depthOf(below)) - depth + 8)
                        * DIRECTION_STEP[d]);
            }
        }
        if (same && depthOf(cell) >= 8) {
            boolean solid = false;
            for (int d = 0; d < 4 && !solid; d++) {
                int[] n = {x, y, z};
                n[DIRECTION_AXIS[d]] += DIRECTION_STEP[d];
                solid = world.block(n[0], n[1], n[2]).fullCube()
                        || world.block(n[0], n[1] + 1, n[2]).fullCube();
            }
            if (solid) {
                float l = (float) Math.sqrt(f[0] * f[0] + f[1] * f[1] + f[2] * f[2]);
                if (l >= 0.0001f) {
                    f[0] /= l;
                    f[1] /= l;
                    f[2] /= l;
                } else {
                    f[0] = f[1] = f[2] = 0.0f;
                }
                f[1] += -6.0f;
            }
        }
        float l = (float) Math.sqrt(f[0] * f[0] + f[1] * f[1] + f[2] * f[2]);
        if (l >= 0.0001f) {
            return new float[]{f[0] / l, f[1] / l, f[2] / l};
        }
        return new float[]{0.0f, 0.0f, 0.0f};
    }

    /** BDS liquid query bounds contraction (collapses to the middle when inverted). */
    static float[] contract(FloatBox box, float horizontal, float vertical) {
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

    /** CollectLiquidCells on raw bounds: any cell of the material whose corner lies inside. */
    static boolean touches(MovementWorldView world, float[] bounds, int material) {
        int bx = floor(bounds[0]);
        int by = floor(bounds[1]);
        int bz = floor(bounds[2]);
        int ex = floor(bounds[3] + 1.0f);
        int ey = floor(bounds[4] + 1.0f);
        int ez = floor(bounds[5] + 1.0f);
        for (int y = by; y < ey; y++) {
            for (int z = bz; z < ez; z++) {
                for (int x = bx; x < ex; x++) {
                    if (world.block(x, y, z).fluid() != material) continue;
                    if ((float) x <= bounds[3] && (float) y <= bounds[4] && (float) z <= bounds[5]) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static int floor(float value) {
        return (int) Math.floor(value);
    }
}
