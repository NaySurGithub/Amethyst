package nay.amethyst.check;

import java.util.ArrayDeque;
import java.util.Locale;

/**
 * Hits on a target moving away, and how many of them only land on a position older than the one
 * the client confirmed. A client holding back its target's movement answers the latency probe that
 * follows each packet at once, so the server knows what it should be seeing: its hits keep landing
 * where the target was.
 */
public final class BacktrackStats {
    private final ArrayDeque<Sample> window = new ArrayDeque<>();
    private long hits;
    private long receding;
    private long rewound;
    private double delayTicks;
    private double gain;
    private double longestDelayTicks;

    /** One hit on a receding target: whether it needed an older position, how old, and what it saved. */
    public record Sample(boolean rewound, double delayTicks, double gain) {
    }

    public record Verdict(boolean failed, String detail) {
        static final Verdict CLEAN = new Verdict(false, "");
    }

    public synchronized void countHit() {
        hits++;
    }

    public synchronized void add(Sample sample, int windowSize) {
        receding++;
        if (sample.rewound()) {
            rewound++;
            delayTicks += sample.delayTicks();
            gain += sample.gain();
            longestDelayTicks = Math.max(longestDelayTicks, sample.delayTicks());
        }
        window.addLast(sample);
        while (window.size() > Math.max(1, windowSize)) {
            window.removeFirst();
        }
    }

    /** Fails once enough of the recent hits needed an older position; the window then starts over. */
    public synchronized Verdict evaluate(int minimumSamples, double ratio) {
        int size = window.size();
        if (size < Math.max(1, minimumSamples)) {
            return Verdict.CLEAN;
        }
        int late = 0;
        double delay = 0.0;
        double longest = 0.0;
        double saved = 0.0;
        for (Sample sample : window) {
            if (!sample.rewound()) continue;
            late++;
            delay += sample.delayTicks();
            longest = Math.max(longest, sample.delayTicks());
            saved += sample.gain();
        }
        if (late == 0 || late < ratio * size) {
            return Verdict.CLEAN;
        }
        window.clear();
        return new Verdict(true, String.format(Locale.ROOT,
                "late=%d/%d ratio=%.2f delay=%.0fms max=%.0fms gain=%.2f",
                late, size, late / (double) size, delay / late * 50.0, longest * 50.0, saved / late));
    }

    public record Snapshot(long hits, long receding, long late, double ratio, double delayMillis,
                           double longestDelayMillis, double gain) {
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(hits, receding, rewound, receding == 0 ? 0.0 : rewound / (double) receding,
                rewound == 0 ? 0.0 : delayTicks / rewound * 50.0, longestDelayTicks * 50.0,
                rewound == 0 ? 0.0 : gain / rewound);
    }

    /** Everything gathered since the player joined. */
    public synchronized String describe() {
        double share = receding == 0 ? 0.0 : rewound / (double) receding;
        double delay = rewound == 0 ? 0.0 : delayTicks / rewound * 50.0;
        double saved = rewound == 0 ? 0.0 : gain / rewound;
        return String.format(Locale.ROOT,
                "hits=%d receding=%d late=%d ratio=%.2f delay=%.0fms max=%.0fms gain=%.2f window=%d",
                hits, receding, rewound, share, delay, longestDelayTicks * 50.0, saved, window.size());
    }
}
