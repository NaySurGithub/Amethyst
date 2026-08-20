package nay.amethyst.tracking.network.ack;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class AcknowledgmentRegistry {
    private static final int MAX_PENDING = 256;
    private static final long TIMESTAMP_MASK = 0xffff_ffffL;
    private final LinkedHashMap<Long, Pending> pending = new LinkedHashMap<>();
    private long nextTimestamp = ThreadLocalRandom.current().nextLong(1, TIMESTAMP_MASK + 1);

    public synchronized long queue(long now, long serverTick, AcknowledgmentType type, Runnable callback) {
        return queue(now, serverTick, type, callback == null ? List.of() : List.of(callback));
    }

    public synchronized long queue(long now, long serverTick, AcknowledgmentType type,
                                   List<Runnable> callbacks) {
        long timestamp;
        do {
            nextTimestamp = nextTimestamp + 1 & TIMESTAMP_MASK;
            if (nextTimestamp == 0) nextTimestamp = 1;
            timestamp = nextTimestamp;
        } while (pending.containsKey(timestamp));
        pending.put(timestamp, new Pending(now, serverTick, type, List.copyOf(callbacks)));
        while (pending.size() > MAX_PENDING) pending.remove(pending.keySet().iterator().next());
        return timestamp;
    }

    public synchronized Acknowledged acknowledge(long timestamp) {
        Pending match = pending.get(timestamp);
        if (match == null) return null;
        List<Runnable> callbacks = new ArrayList<>();
        Iterator<Map.Entry<Long, Pending>> iterator = pending.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, Pending> entry = iterator.next();
            iterator.remove();
            callbacks.addAll(entry.getValue().callbacks());
            if (entry.getKey() == timestamp) break;
        }
        return new Acknowledged(match.sentNanos(), match.serverTick(), match.type(), callbacks);
    }

    public synchronized List<Runnable> expire(long serverTick, long cutoffTicks) {
        if (pending.isEmpty()) return List.of();
        Iterator<Pending> iterator = pending.values().iterator();
        while (iterator.hasNext()) {
            Pending acknowledgment = iterator.next();
            if (serverTick - acknowledgment.serverTick() < cutoffTicks) break;
            iterator.remove();
        }
        return List.of();
    }

    public synchronized void reset() {
        pending.clear();
        nextTimestamp = ThreadLocalRandom.current().nextLong(1, TIMESTAMP_MASK + 1);
    }

    private record Pending(long sentNanos, long serverTick, AcknowledgmentType type,
                           List<Runnable> callbacks) {
    }

    public record Acknowledged(long sentNanos, long serverTick, AcknowledgmentType type,
                               List<Runnable> callbacks) {
    }
}
