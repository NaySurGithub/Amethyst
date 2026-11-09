package nay.amethyst.check;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BacktrackStatsTest {
    private static final BacktrackStats.Sample ON_TIME = new BacktrackStats.Sample(false, 0.0, 0.0);
    private static final BacktrackStats.Sample LATE = new BacktrackStats.Sample(true, 1.5, 0.4);

    @Test
    void hitsOnTheConfirmedPosition_neverFail() {
        BacktrackStats stats = new BacktrackStats();
        for (int hit = 0; hit < 100; hit++) {
            stats.add(ON_TIME, 40);
            assertFalse(stats.evaluate(20, 0.25).failed());
        }
    }

    @Test
    void nothingIsJudged_beforeEnoughHits() {
        BacktrackStats stats = new BacktrackStats();
        for (int hit = 0; hit < 19; hit++) {
            stats.add(LATE, 40);
            assertFalse(stats.evaluate(20, 0.25).failed());
        }
    }

    @Test
    void aQuarterOfLateHits_failsAndStartsOver() {
        BacktrackStats stats = new BacktrackStats();
        for (int hit = 0; hit < 15; hit++) stats.add(ON_TIME, 40);
        for (int hit = 0; hit < 4; hit++) stats.add(LATE, 40);
        assertFalse(stats.evaluate(20, 0.25).failed());

        stats.add(LATE, 40);
        BacktrackStats.Verdict verdict = stats.evaluate(20, 0.25);

        assertTrue(verdict.failed());
        assertTrue(verdict.detail().contains("late=5/20"), verdict.detail());
        assertTrue(verdict.detail().contains("delay=75ms"), verdict.detail());
        assertFalse(stats.evaluate(1, 0.25).failed());
    }

    @Test
    void oldHits_leaveTheWindow() {
        BacktrackStats stats = new BacktrackStats();
        for (int hit = 0; hit < 10; hit++) stats.add(LATE, 40);
        for (int hit = 0; hit < 40; hit++) stats.add(ON_TIME, 40);

        assertFalse(stats.evaluate(20, 0.25).failed());
        assertTrue(stats.describe().contains("late=10"), stats.describe());
    }
}
