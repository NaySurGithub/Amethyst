package nay.amethyst.tracking.world;

import nay.amethyst.history.model.BlockFrame;
import nay.amethyst.history.model.BlockPos;

import java.util.HashMap;
import java.util.Map;

public final class ClientWorldTracker {
    private static final int MAX_ACKNOWLEDGED_BLOCKS = 8192;
    private final Map<Key, BlockFrame> acknowledged = new HashMap<>();
    private final Map<Key, Update> acknowledgedChanges = new HashMap<>();
    private final Map<Key, Update> queued = new HashMap<>();
    private final Map<Key, Long> pending = new HashMap<>();
    private boolean flushScheduled;
    private long sequence;
    private long revision;

    public synchronized boolean queue(int x, int y, int z, int layer, BlockFrame state) {
        return queue(x, y, z, layer, state, null);
    }

    public synchronized boolean queue(int x, int y, int z, int layer, BlockFrame state,
                                      BlockFrame visibleState) {
        Key key = new Key(x, y, z, layer);
        if (!acknowledged.containsKey(key) && visibleState != null) {
            acknowledged.put(key, visibleState);
        }
        Update update = new Update(++sequence, state);
        queued.put(key, update);
        pending.put(key, update.sequence);
        if (flushScheduled) return false;
        flushScheduled = true;
        return true;
    }

    /**
     * Records a change the player made themselves. The client predicts its own placements and breaks
     * without waiting for the server, so holding the old state back would make the simulation walk on
     * a world the player has already left behind.
     */
    public synchronized void applyLocal(int x, int y, int z, int layer, BlockFrame state) {
        Key key = new Key(x, y, z, layer);
        Update update = new Update(++sequence, state);
        acknowledged.put(key, state);
        acknowledgedChanges.put(key, update);
        queued.remove(key);
        pending.remove(key);
        revision++;
    }

    public synchronized Map<Key, Update> drainQueuedUpdates() {
        flushScheduled = false;
        if (queued.isEmpty()) return Map.of();
        Map<Key, Update> batch = Map.copyOf(queued);
        queued.clear();
        return batch;
    }

    public synchronized void acknowledge(Map<Key, Update> updates) {
        if (!updates.isEmpty()) revision++;
        updates.forEach((key, update) -> {
            acknowledged.put(key, update.state);
            acknowledgedChanges.put(key, update);
            if (pending.getOrDefault(key, -1L) == update.sequence) pending.remove(key);
        });
        if (acknowledged.size() > MAX_ACKNOWLEDGED_BLOCKS) acknowledged.clear();
    }

    public synchronized BlockFrame resolve(int x, int y, int z, int layer, BlockFrame serverState) {
        Key key = new Key(x, y, z, layer);
        if (pending.containsKey(key)) {
            return acknowledged.get(key);
        }
        return acknowledged.getOrDefault(key, serverState);
    }

    public synchronized void pruneAround(double x, double y, double z, int horizontal, int vertical) {
        acknowledged.keySet().removeIf(key -> Math.abs(key.x - x) > horizontal
                || Math.abs(key.y - y) > vertical || Math.abs(key.z - z) > horizontal);
        queued.keySet().removeIf(key -> Math.abs(key.x - x) > horizontal
                || Math.abs(key.y - y) > vertical || Math.abs(key.z - z) > horizontal);
        pending.keySet().removeIf(key -> Math.abs(key.x - x) > horizontal
                || Math.abs(key.y - y) > vertical || Math.abs(key.z - z) > horizontal);
    }

    public synchronized long revision() {
        return revision;
    }

    public synchronized Map<Key, Update> drainAcknowledgedChanges() {
        if (acknowledgedChanges.isEmpty()) return Map.of();
        Map<Key, Update> changes = Map.copyOf(acknowledgedChanges);
        acknowledgedChanges.clear();
        return changes;
    }

    public synchronized int retainedBlockCount() {
        return acknowledged.size() + queued.size() + pending.size();
    }

    public synchronized void clear() {
        acknowledged.clear();
        acknowledgedChanges.clear();
        queued.clear();
        pending.clear();
        flushScheduled = false;
        revision++;
    }

    public record Key(int x, int y, int z, int layer) {
        public BlockPos position() {
            return new BlockPos(x, y, z);
        }
    }

    public record Update(long sequence, BlockFrame state) {
    }
}
