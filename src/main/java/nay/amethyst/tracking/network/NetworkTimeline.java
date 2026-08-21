package nay.amethyst.tracking.network;

import nay.amethyst.tracking.network.ack.AcknowledgmentRegistry;
import nay.amethyst.tracking.network.ack.AcknowledgmentType;

import java.util.List;
import java.util.ArrayList;

public final class NetworkTimeline {
    private static final long TICK_NANOS = 50_000_000L;
    private static final long ACK_CUTOFF_TICKS = 60;
    private final AcknowledgmentRegistry acknowledgments = new AcknowledgmentRegistry();
    private long serverTick;
    private long clientTick;
    private long lastServerTickNanos;
    private long nextProbeTick;
    private long lastInputServerTick = -1;
    private long stackLatencyNanos;
    private int allowedInputs = 65535;
    private int overBudgetInputs;
    private int inputCount;
    private boolean receivedFirstInput;
    private long acknowledgmentDivider = 1_000_000L;
    private final List<Runnable> currentAcknowledgments = new ArrayList<>();
    private AcknowledgmentType currentType = AcknowledgmentType.GENERIC;
    private boolean acknowledgmentFlushScheduled;

    public synchronized void tick(long now) {
        long elapsedTicks = lastServerTickNanos == 0 ? 1
                : Math.max(1, (now - lastServerTickNanos) / TICK_NANOS);
        lastServerTickNanos = now;
        serverTick += elapsedTicks;
        if (!receivedFirstInput) {
            allowedInputs = 65535;
            return;
        }

        long latencyAllowance = Math.max(0, serverTick - clientTick) + elapsedTicks;
        long regularAllowance = Math.max(0, allowedInputs) + elapsedTicks;
        allowedInputs = (int) Math.min(Integer.MAX_VALUE, Math.min(latencyAllowance, regularAllowance));
        // Bedrock must always be able to deliver one frame for every server tick.
        allowedInputs = Math.max(1, allowedInputs);
    }

    /**
     * Counts one client frame. An input over the tick budget is still counted: dropping it would hide
     * a tick of movement from the simulation, which then cannot explain the position the next packet
     * reports and blames the player for the gap. The excess is recorded instead.
     */
    public synchronized int acceptInput() {
        if (!receivedFirstInput) {
            receivedFirstInput = true;
        }
        if (allowedInputs > 0) {
            allowedInputs--;
        } else {
            overBudgetInputs++;
        }
        clientTick++;
        inputCount++;
        int elapsedTicks = (int) Math.max(1, Math.min(20, serverTick - lastInputServerTick));
        lastInputServerTick = serverTick;
        return elapsedTicks;
    }

    public synchronized int drainOverBudgetInputs() {
        int total = overBudgetInputs;
        overBudgetInputs = 0;
        return total;
    }

    /** Frames received since the last call, to be compared against the ticks that elapsed. */
    public synchronized int drainInputCount() {
        int total = inputCount;
        inputCount = 0;
        return total;
    }

    public synchronized boolean shouldProbe() {
        if (serverTick < nextProbeTick) return false;
        nextProbeTick = serverTick + 10;
        return true;
    }

    public synchronized long queueAck(long now, Runnable callback) {
        return queueAck(now, AcknowledgmentType.GENERIC, callback);
    }

    public synchronized long queueAck(long now, AcknowledgmentType type, Runnable callback) {
        return acknowledgments.queue(now, serverTick, type, callback);
    }

    public synchronized boolean addAcknowledgment(AcknowledgmentType type, Runnable callback) {
        if (callback != null) {
            currentAcknowledgments.add(callback);
        }
        if (currentType == AcknowledgmentType.GENERIC) {
            currentType = type;
        }
        if (acknowledgmentFlushScheduled) {
            return false;
        }
        acknowledgmentFlushScheduled = true;
        return true;
    }

    public synchronized long flushAcknowledgments(long now) {
        acknowledgmentFlushScheduled = false;
        List<Runnable> callbacks = List.copyOf(currentAcknowledgments);
        currentAcknowledgments.clear();
        AcknowledgmentType type = currentType;
        currentType = AcknowledgmentType.GENERIC;
        return acknowledgments.queue(now, serverTick, type, callbacks);
    }

    public synchronized void discardAcknowledgmentBatch() {
        currentAcknowledgments.clear();
        currentType = AcknowledgmentType.GENERIC;
        acknowledgmentFlushScheduled = false;
    }

    public synchronized List<Runnable> acknowledge(long timestamp, long now) {
        AcknowledgmentRegistry.Acknowledged match = acknowledgments.acknowledge(timestamp);
        if (match == null && acknowledgmentDivider > 1) {
            long normalizedTimestamp = timestamp / acknowledgmentDivider;
            if (normalizedTimestamp != timestamp) {
                match = acknowledgments.acknowledge(normalizedTimestamp);
            }
        }
        if (match == null) return List.of();

        stackLatencyNanos = Math.max(0, now - match.sentNanos());
        // Re-anchor the client clock when a delayed response would otherwise leave
        // permanent simulation credit after the connection recovers.
        if (clientTick < match.serverTick() || clientTick >= serverTick) clientTick = match.serverTick();
        return match.callbacks();
    }

    public synchronized List<Runnable> expireDelayedAcks() {
        return acknowledgments.expire(serverTick, ACK_CUTOFF_TICKS);
    }

    public synchronized long stackLatencyMillis() {
        return stackLatencyNanos / 1_000_000L;
    }

    public synchronized void setSonyClient(boolean sonyClient) {
        acknowledgmentDivider = sonyClient ? 1_000L : 1_000_000L;
    }

    public synchronized void reset() {
        acknowledgments.reset();
        serverTick = 0;
        clientTick = 0;
        lastServerTickNanos = 0;
        nextProbeTick = 0;
        lastInputServerTick = -1;
        stackLatencyNanos = 0;
        allowedInputs = 65535;
        receivedFirstInput = false;
        currentAcknowledgments.clear();
        currentType = AcknowledgmentType.GENERIC;
        acknowledgmentFlushScheduled = false;
    }

}
