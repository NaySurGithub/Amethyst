package nay.amethyst.network;

import java.util.ArrayDeque;
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
    /**
     * Timer: the client's own clock, advanced 50 ms per input and pulled up to the server send
     * time of every acknowledged latency probe (the client cannot be behind a probe it answered).
     * Late, bunched inputs after a lag spike therefore never count as speed; only a clock that
     * runs ahead of real time does.
     */
    private long timerClockNanos;
    private long timerAheadNanos;
    private static final int MAX_STAMPS = 512;
    private static final long DEFERRED_TIMEOUT_NANOS = 250_000_000L;
    /**
     * Answered probes are read on the network thread, every other packet on the server thread a
     * little later. Each packet is numbered as it arrives, and an answered probe remembers how many
     * came before it, so its callbacks run between the same two packets the client sent it between.
     */
    private final ArrayDeque<Stamp> stamps = new ArrayDeque<>();
    private final ArrayDeque<Deferred> deferred = new ArrayDeque<>();
    private long arrivedPackets;
    private long handledPackets;

    private record Stamp(Object packet, long sequence) {
    }

    private record Deferred(long afterPackets, long arrivedNanos, List<Runnable> callbacks) {
    }

    /** Network thread: a packet on its way to the server thread. */
    public synchronized void stampInbound(Object packet) {
        stamps.addLast(new Stamp(packet, ++arrivedPackets));
        if (stamps.size() > MAX_STAMPS) stamps.removeFirst();
    }

    /** Network thread: the callbacks of an answered probe wait for the packets that arrived before it. */
    public synchronized void deferAcknowledged(List<Runnable> callbacks, long now) {
        if (callbacks.isEmpty()) return;
        deferred.addLast(new Deferred(arrivedPackets, now, callbacks));
    }

    /** Server thread, before a packet is handled: the probes answered before it arrived. */
    public synchronized List<Runnable> beginInbound(Object packet) {
        long sequence = -1;
        for (Stamp stamp : stamps) {
            if (stamp.packet() == packet) {
                sequence = stamp.sequence();
                break;
            }
        }
        if (sequence < 0) return due(handledPackets, Long.MIN_VALUE);
        while (!stamps.isEmpty() && stamps.peekFirst().sequence() <= sequence) stamps.removeFirst();
        List<Runnable> callbacks = due(sequence - 1, Long.MIN_VALUE);
        handledPackets = Math.max(handledPackets, sequence);
        return callbacks;
    }

    /** Server thread, once a tick: what no later packet claimed, and anything left waiting too long. */
    public synchronized List<Runnable> dueAcknowledgments(long now) {
        return due(handledPackets, now - DEFERRED_TIMEOUT_NANOS);
    }

    private List<Runnable> due(long handled, long arrivedBefore) {
        List<Runnable> callbacks = null;
        while (!deferred.isEmpty()) {
            Deferred next = deferred.peekFirst();
            if (next.afterPackets() > handled && next.arrivedNanos() > arrivedBefore) break;
            deferred.removeFirst();
            if (callbacks == null) callbacks = new ArrayList<>(next.callbacks());
            else callbacks.addAll(next.callbacks());
        }
        return callbacks == null ? List.of() : callbacks;
    }

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
        advanceTimerClock(System.nanoTime());
        int elapsedTicks = (int) Math.max(1, Math.min(20, serverTick - lastInputServerTick));
        lastInputServerTick = serverTick;
        return elapsedTicks;
    }

    public synchronized int drainOverBudgetInputs() {
        int total = overBudgetInputs;
        overBudgetInputs = 0;
        return total;
    }

    /** Frames received since the last call. */
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
        if (timerClockNanos != 0) timerClockNanos = Math.max(timerClockNanos, match.sentNanos());
        if (clientTick < match.serverTick() || clientTick >= serverTick) clientTick = match.serverTick();
        return match.callbacks();
    }

    public synchronized List<Runnable> expireDelayedAcks() {
        return acknowledgments.expire(serverTick, ACK_CUTOFF_TICKS);
    }

    private void advanceTimerClock(long now) {
        if (timerClockNanos == 0) timerClockNanos = now - TICK_NANOS;
        timerClockNanos += TICK_NANOS;
        timerAheadNanos = Math.max(timerAheadNanos, timerClockNanos - now);
    }

    /** Furthest the client's clock ran ahead of real time since the last call, in nanoseconds. */
    public synchronized long drainTimerAheadNanos() {
        long ahead = timerAheadNanos;
        timerAheadNanos = 0;
        return ahead;
    }

    /** Puts the client's clock back on real time (grace, warmup, after a flag). */
    public synchronized void resetTimerClock(long now) {
        timerClockNanos = now;
        timerAheadNanos = 0;
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
        timerClockNanos = 0;
        timerAheadNanos = 0;
        stamps.clear();
        deferred.clear();
        arrivedPackets = 0;
        handledPackets = 0;
        allowedInputs = 65535;
        receivedFirstInput = false;
        currentAcknowledgments.clear();
        currentType = AcknowledgmentType.GENERIC;
        acknowledgmentFlushScheduled = false;
    }

}
