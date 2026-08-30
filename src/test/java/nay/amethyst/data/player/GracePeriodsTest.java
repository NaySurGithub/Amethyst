package nay.amethyst.data.player;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GracePeriodsTest {

    @Test
    void active_grantedReason_isActiveThenExpiresAfterDuration() throws InterruptedException {
        GracePeriods periods = new GracePeriods();

        periods.grant(GraceReason.TELEPORT, 10L);
        assertTrue(periods.active(GraceReason.TELEPORT),
                "grace period should be active right after being granted");

        Thread.sleep(40L);
        assertFalse(periods.active(GraceReason.TELEPORT),
                "grace period should expire once its duration has elapsed");
    }

    @Test
    void revoke_oneReason_doesNotAffectOtherReasons() {
        GracePeriods periods = new GracePeriods();

        periods.grant(GraceReason.TELEPORT, 5_000L);
        periods.grant(GraceReason.SERVER_CORRECTION, 5_000L);

        periods.revoke(GraceReason.TELEPORT);

        assertFalse(periods.active(GraceReason.TELEPORT),
                "revoked reason should be immediately inactive");
        assertTrue(periods.active(GraceReason.SERVER_CORRECTION),
                "unrelated reason should remain unaffected by another reason's revocation");
    }
}
