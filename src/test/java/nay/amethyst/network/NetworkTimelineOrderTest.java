package nay.amethyst.network;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** An answered probe takes effect between the same two packets it arrived between. */
final class NetworkTimelineOrderTest {
    private final NetworkTimeline timeline = new NetworkTimeline();
    private final List<String> order = new ArrayList<>();

    private void arrive(Object packet) {
        timeline.stampInbound(packet);
    }

    private void answer(String name) {
        timeline.deferAcknowledged(List.of(() -> order.add(name)), System.nanoTime());
    }

    private void handle(Object packet, String name) {
        timeline.beginInbound(packet).forEach(Runnable::run);
        order.add(name);
    }

    @Test
    void probeAnsweredBetweenTwoInputs_runsBetweenThem() {
        Object first = new Object();
        Object second = new Object();
        arrive(first);
        answer("knockback");
        arrive(second);

        handle(first, "input1");
        handle(second, "input2");

        assertEquals(List.of("input1", "knockback", "input2"), order);
    }

    @Test
    void probeAnsweredAfterAnInput_neverRunsBeforeIt() {
        Object input = new Object();
        arrive(input);
        answer("late");

        assertTrue(timeline.dueAcknowledgments(System.nanoTime()).isEmpty());
        handle(input, "input");
        timeline.dueAcknowledgments(System.nanoTime()).forEach(Runnable::run);

        assertEquals(List.of("input", "late"), order);
    }

    @Test
    void probeWithNoPacketBehindIt_runsOnTheTick() {
        answer("alone");

        timeline.dueAcknowledgments(System.nanoTime()).forEach(Runnable::run);

        assertEquals(List.of("alone"), order);
    }

    @Test
    void packetThatNeverReachesTheServerThread_doesNotHoldProbesForever() {
        arrive(new Object());
        answer("stuck");
        Object next = new Object();
        arrive(next);

        handle(next, "next");

        assertEquals(List.of("stuck", "next"), order);
    }

    @Test
    void probeWaitingTooLong_isAppliedAnyway() {
        arrive(new Object());
        timeline.deferAcknowledged(List.of(() -> order.add("old")), System.nanoTime() - 1_000_000_000L);

        timeline.dueAcknowledgments(System.nanoTime()).forEach(Runnable::run);

        assertEquals(List.of("old"), order);
    }
}
