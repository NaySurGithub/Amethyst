package nay.amethyst.check;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class HitboxStatsTest {
    @Test
    void describe_countsEachMarginAndKeepsUnrelatedPointsOutOfTheAverage() {
        HitboxStats stats = new HitboxStats();
        stats.add(0.0, 0.0);
        stats.add(0.06, 0.2);
        stats.add(0.3, 0.0);
        stats.add(40.0, 0.0);

        String text = stats.describe();

        assertTrue(text.contains("hits=4"), text);
        assertTrue(text.contains("click avg=0.1200 max=0.3000"), text);
        assertTrue(text.contains(">0.01=2 >0.05=2 >0.1=1 >0.25=1 >0.5=0 >1=0"), text);
        assertTrue(text.contains("unrelated=1"), text);
        assertTrue(text.contains("origin avg=0.0500 max=0.2000"), text);
    }
}
