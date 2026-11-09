package nay.amethyst.network;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AcknowledgmentRegistryTest {
    @Test
    void expiry_handsBackTheCallbacksItRemoves() {
        AcknowledgmentRegistry registry = new AcknowledgmentRegistry();
        List<String> ran = new ArrayList<>();
        registry.queue(0, 0, AcknowledgmentType.GENERIC, () -> ran.add("old"));
        registry.queue(0, 50, AcknowledgmentType.GENERIC, () -> ran.add("recent"));

        registry.expire(60, 60).forEach(Runnable::run);

        assertEquals(List.of("old"), ran);
        assertTrue(registry.expire(60, 60).isEmpty());
    }

    @Test
    void overflow_keepsEveryCallbackInOrder() {
        AcknowledgmentRegistry registry = new AcknowledgmentRegistry();
        List<Integer> ran = new ArrayList<>();
        long last = 0;
        for (int index = 0; index < 300; index++) {
            int value = index;
            last = registry.queue(0, 0, AcknowledgmentType.GENERIC, () -> ran.add(value));
        }

        registry.acknowledge(last).callbacks().forEach(Runnable::run);

        assertEquals(300, ran.size());
        for (int index = 0; index < 300; index++) {
            assertEquals(index, ran.get(index));
        }
    }
}
