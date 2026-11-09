package nay.amethyst.check;

import java.util.Locale;

/**
 * What attacks report about themselves: how far the hit point lies outside the target's confirmed
 * hitbox, and how far the origin lies from where the attacker's own inputs put it.
 */
public final class HitboxStats {
    /** Past this the point has nothing to do with the target: it is counted, never averaged. */
    public static final double UNRELATED = 5.0;
    private static final double[] STEPS = {0.01, 0.05, 0.1, 0.25, 0.5, 1.0};

    private final long[] beyond = new long[STEPS.length];
    private long hits;
    private long unrelated;
    private double clickSum;
    private double clickMax;
    private double originSum;
    private double originMax;

    public synchronized void add(double click, double origin) {
        hits++;
        originSum += origin;
        originMax = Math.max(originMax, origin);
        if (click >= UNRELATED) {
            unrelated++;
            return;
        }
        clickSum += click;
        clickMax = Math.max(clickMax, click);
        for (int index = 0; index < STEPS.length; index++) {
            if (click > STEPS[index]) beyond[index]++;
        }
    }

    public synchronized String describe() {
        long related = hits - unrelated;
        StringBuilder text = new StringBuilder(String.format(Locale.ROOT,
                "hits=%d click avg=%.4f max=%.4f", hits,
                related == 0 ? 0.0 : clickSum / related, clickMax));
        for (int index = 0; index < STEPS.length; index++) {
            text.append(String.format(Locale.ROOT, " >%s=%d", trim(STEPS[index]), beyond[index]));
        }
        text.append(String.format(Locale.ROOT, " unrelated=%d | origin avg=%.4f max=%.4f",
                unrelated, hits == 0 ? 0.0 : originSum / hits, originMax));
        return text.toString();
    }

    private static String trim(double value) {
        return String.valueOf(value).replaceAll("\\.?0+$", "");
    }
}
