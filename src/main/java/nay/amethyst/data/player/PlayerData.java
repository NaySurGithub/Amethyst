package nay.amethyst.data.player;

import nay.amethyst.prediction.common.Vec3;
import nay.amethyst.history.timeline.CompensatedHistory;
import nay.amethyst.tracking.entity.ClientEntityTracker;
import nay.amethyst.tracking.network.NetworkTimeline;
import nay.amethyst.tracking.world.ClientWorldTracker;
import nay.amethyst.tracking.player.ClientPlayerState;
import nay.amethyst.simulation.movement.AuthoritativeMotionState;
import nay.amethyst.simulation.movement.AuthoritativeMovementPipeline;
import nay.amethyst.simulation.movement.MovementOptions;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.packet.SetActorDataPacket;
import org.powernukkitx.level.Location;

import java.util.HashMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

public final class PlayerData {
    private static final int CLICK_WINDOW_TICKS = 20;
    private static final long PROTOCOL_GRACE_MILLIS = 3000;
    private static final long VELOCITY_GRACE_MILLIS = 1500;
    public static final long SPEAR_LUNGE_SEQUENCE = Long.MAX_VALUE - 1;
    public static final long SERVER_MOTION_SEQUENCE = Long.MAX_VALUE - 2;
    public static final long SERVER_MOTION_STOP_SEQUENCE = Long.MAX_VALUE - 3;
    public boolean joined;
    public boolean touchInput;
    public boolean modernItemUseProtocol;
    public boolean setbackTeleportPending;
    private boolean movementCorrectionPending;
    private long movementCorrectionDeadline;
    private boolean simulationCorrectionEpisode;
    public int simulationMismatchFrames;
    public double simulationOffsetBuffer;
    public boolean nearServerMotionTick;
    public volatile boolean movementPacketDropped;
    public volatile boolean kickScheduled;
    public volatile boolean meleeKnockbackPending;
    public Vec3 expectedMeleeKnockback;
    public int meleeKnockbackTicks;
    public double meleeKnockbackObserved;
    public double meleeKnockbackExpected;
    public double velocityBuffer;
    public double timerBuffer;
    public int timerWarmup;
    public int timerInputs;
    public int timerTicks;
    public int groundSpoofBuffer;
    public int cobwebBuffer;
    public int sprintFoodBuffer;
    public int sprintUseBuffer;
    public boolean wasSprinting;
    public boolean wasLevitating;
    public long lastGlideTick = Long.MIN_VALUE;
    public long lastSleepingTick = Long.MIN_VALUE;
    public boolean inBed;
    public int vehicleClaimBuffer;
    private final int[] leftClicks = new int[CLICK_WINDOW_TICKS];
    private final int[] rightClicks = new int[CLICK_WINDOW_TICKS];
    private int clickSlot;
    private int leftCps;
    private int rightCps;
    public long lastGlideStartTick = Long.MIN_VALUE;
    public long itemUseStartTick = Long.MIN_VALUE;
    /** Last ground position the simulation agreed with, and the target of a setback. */
    public Vec3 lastVerifiedPosition;
    public Vec3 lastVerifiedVehiclePosition;
    private DirectSetback directSetback;
    private int pendingTeleportAcks;
    private long pendingTeleportDeadline;
    public boolean sawNonZeroClientTick;
    public boolean lastDirectionalInput;
    public boolean inventoryMovePrepared;
    public long inventoryMoveRequestNanos;
    public int inventoryMoveBuffer;
    public final java.util.ArrayDeque<Long> chestClickTimestamps = new java.util.ArrayDeque<>();
    public int chestStealerBuffer;
    public long lastChestTakeNanos;
    public int chestFastStreak;
    public long containerOpenedNanos;
    public long lastRidingNanos;
    public long lastTotemPopNanos;
    public int autoTotemBuffer;
    public long lastCombatNanos;
    public long lastTick = -1;
    public long inputSequence;
    public long lastGroundedInputSequence = -1;
    public long lastRecoveredJumpTick = Long.MIN_VALUE;
    public boolean jumpConsumed;
    public int jumpDelayTicks;
    public long lastGroundSyncTick = Long.MIN_VALUE;
    public Vector3i breakingBlock;
    public String breakingBlockId;
    public long breakStartedNanos;
    public long requiredBreakNanos;
    public double breakProgress;
    public long breakProgressTick = Long.MIN_VALUE;
    public int earlyBreakAttempts;
    public long earlyBreakWindowNanos;
    public int timerSamples;
    public int timerOverLimitSamples;
    public Vector3f lastPosition;
    public Vec3 authoritativePosition;
    public Vec3 predictedVelocity = Vec3.ZERO;
    public boolean predictedOnGround;
    public boolean predictedHorizontalCollision;
    public boolean predictedSprinting;
    public boolean predictedSneaking;
    public boolean clientSwimming;
    public boolean clientCrawling;
    public boolean movementInputStateInitialized;
    public double predictedMovementSpeed = Double.NaN;
    public boolean clientServerSprinting;
    public boolean clientServerSprintApplied = true;
    public double clientWidth = Double.NaN;
    public double clientHeight = Double.NaN;
    public double clientScale = Double.NaN;
    public boolean stableGroundContact;
    public boolean penetratedLastFrame;
    public boolean stuckInCollider;
    public double clientMovementSpeed = Double.NaN;
    public double clientDefaultMovementSpeed = Double.NaN;
    public Integer clientJumpBoostAmplifier;
    public boolean clientJumpBoostKnown;
    public Integer clientLevitationAmplifier;
    public boolean clientLevitationKnown;
    public boolean clientSlowFalling;
    public boolean clientSlowFallingKnown;
    public boolean gliding;
    public int glideBoostTicks;
    public Location safeLocation;
    public SetActorDataPacket lastActorData;
    private final GracePeriods gracePeriods = new GracePeriods();
    public long lastAlertNanos;
    public long vehicleId = -1;
    public boolean vehicleInputConfirmed;
    public int vehicleWarmupPackets;
    public Vec3 predictedVehicleVelocity = Vec3.ZERO;
    public double vehicleBuffer;
    public double simulatedFallDistance;
    public long pendingFallTick = -1;
    public long pendingFallInputSequence;
    public long pendingFallDeadlineNanos;
    public int noFallBuffer;
    public double pendingFallDistance;
    public double pendingFallDamage;
    public boolean fallEventObserved;
    public boolean fallEventCancelled;
    public double observedServerFallDistance;
    public boolean fallDamageObserved;
    public double observedFallDamage;
    public final Map<String, Double> violations = new HashMap<>();
    public final CompensatedHistory history = new CompensatedHistory();
    public final ClientEntityTracker clientEntities = new ClientEntityTracker();
    public final ClientWorldTracker clientWorld = new ClientWorldTracker();
    public final ClientPlayerState clientPlayer = new ClientPlayerState();
    public final NetworkTimeline network = new NetworkTimeline();
    public AuthoritativeMotionState motion = new AuthoritativeMotionState();
    public AuthoritativeMovementPipeline movementPipeline = new AuthoritativeMovementPipeline(
            motion, MovementOptions.defaults());
    private final Deque<VelocityImpulse> velocityImpulses = new ArrayDeque<>();
    private final List<Vec3> queuedVelocities = new ArrayList<>();
    private boolean velocityFlushScheduled;
    private int pendingVelocityAcks;
    private long velocitySequence;
    private long velocityEpoch;
    private List<Vec3> windChargeCandidates = List.of();
    private long windChargeUntilInputSequence;
    private Vec3 spearLungeCandidate;
    private long spearLungeUntilInputSequence;
    private final List<Vec3> serverMotionCandidates = new ArrayList<>();
    private long serverMotionUntilInputSequence;
    public synchronized void addPendingTeleport() {
        pendingTeleportAcks++;
        pendingTeleportDeadline = inputSequence + 60;
        gracePeriods.grant(GraceReason.TELEPORT, PROTOCOL_GRACE_MILLIS);
    }

    public synchronized void acknowledgePendingTeleport() {
        if (pendingTeleportAcks > 0) pendingTeleportAcks--;
        if (pendingTeleportAcks == 0) {
            pendingTeleportDeadline = 0;
        }
    }

    public synchronized boolean hasPendingTeleport() {
        if (pendingTeleportAcks > 0 && inputSequence > pendingTeleportDeadline) {
            pendingTeleportAcks = 0;
            pendingTeleportDeadline = 0;
        }
        return pendingTeleportAcks > 0;
    }

    public synchronized boolean beginMovementCorrection() {
        if (hasMovementCorrection()) return false;
        movementCorrectionPending = true;
        movementCorrectionDeadline = inputSequence + 60;
        gracePeriods.grant(GraceReason.SERVER_CORRECTION, PROTOCOL_GRACE_MILLIS);
        return true;
    }

    public synchronized boolean hasMovementCorrection() {
        if (movementCorrectionPending && inputSequence > movementCorrectionDeadline) {
            movementCorrectionPending = false;
            movementCorrectionDeadline = 0;
            gracePeriods.revoke(GraceReason.SERVER_CORRECTION);
        }
        return movementCorrectionPending;
    }

    public synchronized void finishMovementCorrection() {
        movementCorrectionPending = false;
        movementCorrectionDeadline = 0;
        gracePeriods.revoke(GraceReason.SERVER_CORRECTION);
    }

    public synchronized boolean beginSimulationCorrectionEpisode() {
        if (simulationCorrectionEpisode) return false;
        simulationCorrectionEpisode = true;
        return true;
    }

    public synchronized void finishSimulationCorrectionEpisode() {
        simulationCorrectionEpisode = false;
        simulationMismatchFrames = 0;
    }

    public synchronized void stageDirectSetback(Vector3f packetPosition, boolean onGround) {
        directSetback = new DirectSetback(packetPosition, onGround);
    }

    public synchronized DirectSetback consumeDirectSetback() {
        DirectSetback setback = directSetback;
        directSetback = null;
        return setback;
    }

    public synchronized void clearDirectSetback() {
        directSetback = null;
    }

    public synchronized void enqueueVelocity(Vec3 velocity) {
        gracePeriods.grant(GraceReason.VELOCITY, VELOCITY_GRACE_MILLIS);
        velocityImpulses.addLast(new VelocityImpulse(++velocitySequence, inputSequence,
                System.nanoTime(), velocity));
        while (velocityImpulses.size() > 8) velocityImpulses.removeFirst();
    }

    public synchronized boolean queueVelocity(Vec3 velocity) {
        queuedVelocities.add(velocity);
        if (velocityFlushScheduled) return false;
        velocityFlushScheduled = true;
        return true;
    }

    public synchronized long velocityEpoch() {
        return velocityEpoch;
    }

    public synchronized boolean isVelocityEpoch(long epoch) {
        return velocityEpoch == epoch;
    }

    public synchronized void acknowledgeVelocities(List<Vec3> velocities) {
        if (pendingVelocityAcks > 0) pendingVelocityAcks--;
        if (velocities.isEmpty()) return;
        velocityImpulses.clear();
        enqueueVelocity(velocities.get(velocities.size() - 1));
    }

    public synchronized boolean hasPendingVelocity() {
        return !velocityImpulses.isEmpty();
    }

    public synchronized void clearVelocities() {
        velocityEpoch++;
        velocityImpulses.clear();
        queuedVelocities.clear();
        velocityFlushScheduled = false;
        pendingVelocityAcks = 0;
        windChargeCandidates = List.of();
        windChargeUntilInputSequence = 0;
        spearLungeCandidate = null;
        spearLungeUntilInputSequence = 0;
        serverMotionCandidates.clear();
        serverMotionUntilInputSequence = 0;
        gracePeriods.revoke(GraceReason.VELOCITY);
    }

    public synchronized void setWindChargeCandidates(List<Vec3> candidates) {
        windChargeCandidates = List.copyOf(candidates);
        windChargeUntilInputSequence = inputSequence + 12;
    }

    public synchronized List<Vec3> windChargeCandidates() {
        if (inputSequence > windChargeUntilInputSequence) {
            windChargeCandidates = List.of();
        }
        return windChargeCandidates;
    }

    public synchronized void clearWindChargeCandidates() {
        windChargeCandidates = List.of();
        windChargeUntilInputSequence = 0;
    }

    /** Drops the oldest tick of the rolling second and opens a fresh one. */
    public synchronized void tickClicks() {
        clickSlot = (clickSlot + 1) % CLICK_WINDOW_TICKS;
        leftCps -= leftClicks[clickSlot];
        rightCps -= rightClicks[clickSlot];
        leftClicks[clickSlot] = 0;
        rightClicks[clickSlot] = 0;
    }

    public synchronized void clickLeft() {
        leftClicks[clickSlot]++;
        leftCps++;
    }

    public synchronized int clickRight() {
        rightClicks[clickSlot]++;
        return ++rightCps;
    }

    public synchronized int leftCps() {
        return leftCps;
    }

    public synchronized void setSpearLungeCandidate(Vec3 candidate) {
        spearLungeCandidate = candidate;
        spearLungeUntilInputSequence = inputSequence + 10;
    }

    public synchronized Vec3 spearLungeCandidate() {
        if (inputSequence > spearLungeUntilInputSequence) clearSpearLungeCandidate();
        return spearLungeCandidate;
    }

    public synchronized void clearSpearLungeCandidate() {
        spearLungeCandidate = null;
        spearLungeUntilInputSequence = 0;
    }

    public synchronized void addServerMotionCandidate(Vec3 candidate) {
        serverMotionCandidates.add(candidate);
        while (serverMotionCandidates.size() > 6) serverMotionCandidates.removeFirst();
        serverMotionUntilInputSequence = inputSequence + 12;
    }

    public synchronized List<Vec3> serverMotionCandidates() {
        if (inputSequence > serverMotionUntilInputSequence) clearServerMotionCandidates();
        return List.copyOf(serverMotionCandidates);
    }

    public synchronized boolean matchesServerMotion(Vec3 velocity) {
        return serverMotionCandidates().stream().anyMatch(candidate -> candidate.distance(velocity) <= 0.01);
    }

    /** Consumes a server impulse candidate closely matching the observed movement. */
    public synchronized boolean claimMatchingServerMotion(Vec3... observedMotions) {
        for (Vec3 candidate : serverMotionCandidates()) {
            for (Vec3 observed : observedMotions) {
                if (matchesServerMotion(candidate, observed)) {
                    clearServerMotionCandidates();
                    return true;
                }
            }
        }
        return false;
    }

    /** Whether the movement heads roughly along an armed impulse, without consuming it. */
    public synchronized boolean nearServerMotion(Vec3... observedMotions) {
        for (Vec3 candidate : serverMotionCandidates()) {
            for (Vec3 observed : observedMotions) {
                if (alignedWithServerMotion(candidate, observed)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean matchesServerMotion(Vec3 candidate, Vec3 observed) {
        return Math.sqrt(observed.lengthSquared()) >= 0.08
                && candidate.distance(observed) <= 0.15;
    }

    private static boolean alignedWithServerMotion(Vec3 candidate, Vec3 observed) {
        double candidateLength = Math.sqrt(candidate.lengthSquared());
        double observedLength = Math.sqrt(observed.lengthSquared());
        if (candidateLength < 0.15 || observedLength < 0.15) {
            return false;
        }

        double scale = observedLength / candidateLength;
        if (scale < 0.6 || scale > 1.4) {
            return false;
        }

        double alignment = (candidate.x() * observed.x()
                + candidate.y() * observed.y()
                + candidate.z() * observed.z()) / (candidateLength * observedLength);
        return alignment >= 0.9;
    }

    public synchronized void clearServerMotionCandidates() {
        serverMotionCandidates.clear();
        serverMotionUntilInputSequence = 0;
    }

    /** Resets transient check state and starts an exemption for the supplied transition. */
    public void grantGrace(GraceReason reason, long millis) {
        gracePeriods.clear();
        gracePeriods.grant(reason, millis);
        timerSamples = 0;
        timerOverLimitSamples = 0;
        inputSequence = 0;
        pendingTeleportAcks = 0;
        pendingTeleportDeadline = 0;
        movementCorrectionPending = false;
        movementCorrectionDeadline = 0;
        simulationCorrectionEpisode = false;
        simulationMismatchFrames = 0;
        simulationOffsetBuffer = 0.0;
        nearServerMotionTick = false;
        lastVerifiedPosition = null;
        lastVerifiedVehiclePosition = null;
        meleeKnockbackPending = false;
        expectedMeleeKnockback = null;
        meleeKnockbackTicks = 0;
        meleeKnockbackObserved = 0.0;
        meleeKnockbackExpected = 0.0;
        velocityBuffer = 0.0;
        timerBuffer = 0.0;
        timerWarmup = 0;
        timerInputs = 0;
        timerTicks = 0;
        groundSpoofBuffer = 0;
        cobwebBuffer = 0;
        sprintFoodBuffer = 0;
        sprintUseBuffer = 0;
        wasSprinting = false;
        wasLevitating = false;
        lastGlideTick = Long.MIN_VALUE;
        lastSleepingTick = Long.MIN_VALUE;
        inBed = false;
        vehicleClaimBuffer = 0;
        lastGlideStartTick = Long.MIN_VALUE;
        itemUseStartTick = Long.MIN_VALUE;
        directSetback = null;
        resetMovementPipeline();
        lastGroundedInputSequence = -1;
        noFallBuffer = 0;
        lastRecoveredJumpTick = Long.MIN_VALUE;
        jumpConsumed = false;
        jumpDelayTicks = 0;
        lastGroundSyncTick = Long.MIN_VALUE;
        lastDirectionalInput = false;
        inventoryMovePrepared = false;
        inventoryMoveRequestNanos = 0;
        inventoryMoveBuffer = 0;
        chestClickTimestamps.clear();
        chestStealerBuffer = 0;
        lastChestTakeNanos = 0;
        chestFastStreak = 0;
        containerOpenedNanos = 0;
        lastRidingNanos = 0;
        lastTotemPopNanos = 0;
        autoTotemBuffer = 0;
        lastCombatNanos = 0;
        predictedVelocity = Vec3.ZERO;
        authoritativePosition = null;
        penetratedLastFrame = false;
        stuckInCollider = false;
        gliding = false;
        glideBoostTicks = 0;
        predictedOnGround = false;
        predictedHorizontalCollision = false;
        predictedSprinting = false;
        predictedSneaking = false;
        clientSwimming = false;
        clientCrawling = false;
        movementInputStateInitialized = false;
        predictedMovementSpeed = Double.NaN;
        clientServerSprinting = false;
        clientServerSprintApplied = true;
        clientWidth = Double.NaN;
        clientHeight = Double.NaN;
        clientScale = Double.NaN;
        stableGroundContact = false;
        clientMovementSpeed = Double.NaN;
        clientDefaultMovementSpeed = Double.NaN;
        clientJumpBoostAmplifier = null;
        clientJumpBoostKnown = false;
        clientLevitationAmplifier = null;
        clientLevitationKnown = false;
        clientSlowFalling = false;
        clientSlowFallingKnown = false;
        resetVehicle();
        resetFall();
        resetBreak();
        earlyBreakAttempts = 0;
        earlyBreakWindowNanos = 0;
        history.clear();
        clientWorld.clear();
        clearVelocities();
    }

    public synchronized void resetMovementPipeline() {
        motion = new AuthoritativeMotionState();
        movementPipeline = new AuthoritativeMovementPipeline(motion, MovementOptions.defaults());
    }

    public record DirectSetback(Vector3f packetPosition, boolean onGround) {
    }

    /** Returns whether a transition that globally exempts checks is still active. */
    public boolean inGrace() {
        return gracePeriods.active(GraceReason.TELEPORT)
                || gracePeriods.active(GraceReason.WORLD_CHANGE)
                || gracePeriods.active(GraceReason.CHUNK_LOADING)
                || gracePeriods.active(GraceReason.EFFECT_CHANGE);
    }

    /** Returns whether the supplied reason-specific exemption is still active. */
    public boolean inGrace(GraceReason reason) {
        return gracePeriods.active(reason);
    }

    /** Extends a reason-specific exemption without resetting tracked player state. */
    public void grantGracePeriod(GraceReason reason, long millis) {
        gracePeriods.grant(reason, millis);
    }

    public void resetVehicle() {
        vehicleId = -1;
        vehicleInputConfirmed = false;
        vehicleWarmupPackets = 0;
        predictedVehicleVelocity = Vec3.ZERO;
        vehicleBuffer = 0;
        penetratedLastFrame = false;
        stuckInCollider = false;
    }

    public void resetFall() {
        simulatedFallDistance = 0;
        pendingFallTick = -1;
        pendingFallInputSequence = 0;
        pendingFallDeadlineNanos = 0;
        pendingFallDistance = 0;
        pendingFallDamage = 0;
        fallEventObserved = false;
        fallEventCancelled = false;
        observedServerFallDistance = 0;
        fallDamageObserved = false;
        observedFallDamage = 0;
    }

    public void resetBreak() {
        breakingBlock = null;
        breakingBlockId = null;
        breakStartedNanos = 0;
        requiredBreakNanos = 0;
        breakProgress = 0;
        breakProgressTick = Long.MIN_VALUE;
    }
}
