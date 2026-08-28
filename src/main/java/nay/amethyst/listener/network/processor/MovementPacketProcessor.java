package nay.amethyst.listener.network.processor;

import nay.amethyst.AmethystPlugin;
import nay.amethyst.check.type.CheckType;
import nay.amethyst.data.player.PlayerData;
import nay.amethyst.data.player.GraceReason;
import nay.amethyst.listener.network.support.MovementCheckSupport;
import nay.amethyst.listener.network.support.NetworkCheckSupport;
import nay.amethyst.listener.network.support.VehiclePositionSupport;
import nay.amethyst.packet.movement.MovementPreValidationResult;
import nay.amethyst.packet.movement.MovementInputAdapter;
import nay.amethyst.prediction.common.Vec3;
import nay.amethyst.prediction.vehicle.VehiclePredictionResult;
import nay.amethyst.prediction.vehicle.VehiclePredictor;
import nay.amethyst.simulation.movement.FloatVector;
import nay.amethyst.simulation.movement.FrameWorldView;
import nay.amethyst.simulation.movement.MovementConstants;
import nay.amethyst.simulation.movement.MovementInputFlag;
import nay.amethyst.simulation.movement.MovementInputFrame;
import nay.amethyst.simulation.movement.MovementPipelineResult;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.GameType;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.data.payload.inventory.transaction.data.ItemUseInventoryTransaction;
import org.cloudburstmc.protocol.bedrock.data.payload.inventory.transaction.ItemUseActionType;
import org.cloudburstmc.protocol.bedrock.packet.InventoryTransactionPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;
import org.powernukkitx.Player;
import org.powernukkitx.entity.Entity;
import org.powernukkitx.entity.effect.EffectType;
import org.powernukkitx.event.server.PacketReceiveEvent;
import org.powernukkitx.event.player.PlayerTeleportEvent;
import org.powernukkitx.item.ItemID;
import org.powernukkitx.item.Item;
import org.powernukkitx.item.enchantment.Enchantment;
import org.powernukkitx.level.Location;
import org.powernukkitx.level.GameRule;
import org.powernukkitx.math.Vector3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MovementPacketProcessor {
    private static final double BURST_MATCH_DISTANCE = 0.35;
    private static final long TELEPORT_GRACE_MILLIS = 3000;
    private static final double DESYNC_OFFSET = 8.0;
    private static final int VELOCITY_OBSERVE_TICKS = 4;
    private static final double VELOCITY_MINIMUM_RATIO = 0.5;
    private static final double VELOCITY_MAXIMUM_RATIO = 1.8;
    private static final double VELOCITY_BUFFER = 3.0;
    private static final double VELOCITY_MINIMUM_PUSH = 0.15;
    private static final double VELOCITY_MAXIMUM_PUSH = 1.5;
    private static final long IMPULSE_TOLERANCE_TICKS = 30;
    private static final double DEFAULT_MOVEMENT_SPEED = 0.1;
    private static final double COBWEB_MULTIPLIER = 0.25;
    private static final double COBWEB_SPEED_ALLOWANCE = 3.0;
    private static final double COBWEB_JUMP_ALLOWANCE = 0.2;
    private static final int COBWEB_TICKS = 2;
    private static final double FLUID_TOLERANCE = 8.0;
    private static final int SPRINT_MINIMUM_FOOD = 6;
    private static final int SPRINT_TICKS = 4;
    private static final int ELYTRA_START_TICKS = 2;
    private static final double GLIDE_TOLERANCE = 8.0;
    private static final long LEVITATION_GRACE_MILLIS = 3000;
    private static final int GLIDE_TAIL_TICKS = 20;
    private static final int SLEEP_GRACE_TICKS = 100;
    private static final double MAX_SIMULATED_MOVEMENT_SPEED = 0.5;
    private static final int GROUND_SPOOF_TICKS = 6;

    private final AmethystPlugin plugin;
    private final Map<UUID, PlayerData> players;
    private final ViolationHandler violations;
    private final VehiclePredictor vehiclePredictor = new VehiclePredictor();

    public MovementPacketProcessor(AmethystPlugin plugin, Map<UUID, PlayerData> players,
                                   ViolationHandler violations) {
        this.plugin = plugin;
        this.players = players;
        this.violations = violations;
    }

    public void inspectMovement(PacketReceiveEvent event, Player player, PlayerAuthInputPacket packet,
                               BlockPacketProcessor blockProcessor) {
        PlayerData data = players.get(player.getUniqueId());
        if (data == null) {
            return;
        }
        Vector3f clientPosition = packet.getPosition();
        Vector3f rotation = packet.getPlayerRotation();
        Vector3f delta = packet.getPosDelta();
        if (!MovementCheckSupport.finite(clientPosition)
                || !MovementCheckSupport.finite(rotation)
                || !MovementCheckSupport.finite(delta)) {
            violations.fail(event, player, data, CheckType.BAD_PACKET_A, 4,
                    "non-finite movement", true, true);
            return;
        }
        if (Math.abs(rotation.getX()) > 90.01f
                || Math.abs(rotation.getY()) > 3600.0f
                || Math.abs(rotation.getZ()) > 3600.0f) {
            violations.fail(event, player, data, CheckType.BAD_PACKET_B, 2,
                    "invalid rotation", true, true);
            return;
        }

        long tick = packet.getClientTick();
        if (data.lastTick >= 0 && tick <= data.lastTick) {
            boolean moved = data.lastPosition != null
                    && MovementCheckSupport.squared(clientPosition.getX(), clientPosition.getY(),
                    clientPosition.getZ(), data.lastPosition.getX(), data.lastPosition.getY(),
                    data.lastPosition.getZ()) > 1.0E-8;
            if (moved) {
                violations.fail(event, player, data, CheckType.BAD_PACKET_B, 2,
                        "movement on stale tick=" + tick + " last=" + data.lastTick, true, true);
            } else {
                event.setCancelled();
            }
            return;
        }

        data.inputSequence++;
        if (data.predictedOnGround) {
            data.lastGroundedInputSequence = data.inputSequence;
        }
        if (validatePendingFall(event, player, data, tick)) {
            return;
        }
        blockProcessor.inspectActions(event, player, data, packet, clientPosition);
        if (event.isCancelled()) {
            return;
        }
        if (player.getRiding() != null) {
            inspectVehicleMovement(event, player, data, packet, tick, 1,
                    clientPosition, rotation);
            return;
        }
        if (data.vehicleId != -1) {
            data.resetVehicle();
            data.resetFall();
            data.lastPosition = null;
            data.authoritativePosition = null;
            data.resetMovementPipeline();
        }

        MovementInputFrame input = MovementInputAdapter.capture(packet);
        updateClientPose(data, input);
        synchronizeMotionContext(player, data);
        if (!data.hasPendingTeleport() && data.motion.pendingTeleports() > 0) {
            data.motion.clearPendingTeleports();
        }
        Vec3 observedDelta = new Vec3(delta.getX(), delta.getY(), delta.getZ());
        Vec3 observedMovement = data.lastPosition == null ? observedDelta : new Vec3(
                clientPosition.getX() - data.lastPosition.getX(),
                clientPosition.getY() - data.lastPosition.getY(),
                clientPosition.getZ() - data.lastPosition.getZ());
        applyBurstCandidates(data, observedMovement);
        boolean serverMotionRecovery = data.claimMatchingServerMotion(
                observedDelta, observedMovement);
        data.nearServerMotionTick = !serverMotionRecovery
                && data.nearServerMotion(observedDelta, observedMovement);
        boolean bypassed = data.inGrace() || serverMotionRecovery
                || MovementCheckSupport.riptideAvailable(player);
        if (bypassed) {
            data.motion.ready(false);
            data.motion.updateInput(input);
            data.motion.resetToClient();
            data.motion.finishInput();
            acceptPipelineState(data, clientPosition, delta, player.isOnGround());
        } else {
            data.motion.ready(true);
            ensureMovementWorldFrame(player, data, tick, clientPosition, rotation);
            var worldFrame = data.history.latest();
            if (worldFrame == null) {
                acceptPipelineState(data, clientPosition, delta, player.isOnGround());
            } else {
                boolean previousGround = data.predictedOnGround;
                MovementPipelineResult result = data.movementPipeline.handle(
                        input, new FrameWorldView(worldFrame));
                FloatVector forwarded = result.forwardedPosition();
                packet.setPosition(Vector3f.from(forwarded.x(), forwarded.y(), forwarded.z()));
                data.authoritativePosition = new Vec3(forwarded.x(), forwarded.y(), forwarded.z());
                data.predictedVelocity = new Vec3(result.authoritativeVelocity().x(),
                        result.authoritativeVelocity().y(), result.authoritativeVelocity().z());
                data.predictedOnGround = result.onGround();
                data.predictedHorizontalCollision = data.motion.collideX()
                        || data.motion.collideZ();
                data.penetratedLastFrame = data.motion.penetratedLastFrame();
                data.stuckInCollider = data.motion.stuckInCollider();
                if (data.motion.wearingElytra() && !result.onGround()) {
                    data.lastGlideTick = data.lastTick;
                }
                inspectGroundSpoof(event, player, data, clientPosition);
                inspectCobweb(event, player, data, clientPosition, observedMovement);
                inspectSprint(event, player, data);
                trackLevitation(player, data);
                inspectElytra(event, player, data, packet);
                measureMeleeKnockback(event, player, data, result, observedMovement);
                accumulateSimulationOffset(event, player, data, result);
                if (result.correctionRequired() && !data.nearServerMotionTick
                        && !beyondSimulatedSpeed(player) && !recentlyGliding(data)) {
                    data.simulationMismatchFrames++;
                    if (data.simulationMismatchFrames >= 2) {
                        data.simulationMismatchFrames = 0;
                        sendSimulationCorrection(event, player, data, result);
                        return;
                    }
                } else {
                    data.simulationMismatchFrames = 0;
                    if (result.offset() <= MovementConstants.CORRECTION_THRESHOLD
                            && !data.hasMovementCorrection()) {
                        data.finishSimulationCorrectionEpisode();
                    }
                }
                if (result.offset() > MovementConstants.CORRECTION_THRESHOLD) {
                    data.resetFall();
                } else {
                    updateFallPrediction(player, data, packet.getPosition(),
                            previousGround, result.onGround(), tick);
                }
            }
        }

        data.lastTick = tick;
        data.lastPosition = clientPosition;
        Vector3f accepted = packet.getPosition();
        if (!MovementCheckSupport.collides(player, accepted)
                && (data.predictedOnGround || MovementCheckSupport.serverGround(player, accepted))) {
            data.safeLocation = MovementCheckSupport.clientLocation(player, accepted, rotation);
        }
        captureMovementWorldFrame(player, data, tick, accepted, rotation);
        if (data.inputSequence % 100 == 0) {
            data.clientWorld.pruneAround(accepted.getX(), accepted.getY(), accepted.getZ(), 64, 32);
        }
    }

    public void handleEarlyMovementRejection(Player player, MovementPreValidationResult result) {
        if (result.check() == null || !player.isOnline()
                || player.hasPermission("amethyst.bypass")
                || plugin.settings().disabled(result.check().id())) return;
        PlayerData data = players.get(player.getUniqueId());
        if (data == null || !data.joined) return;
        data.movementPacketDropped = true;
        double vl = data.violations.merge(result.check().id(), result.violationAmount(), Double::sum);
        long now = System.nanoTime();
        if (now - data.lastAlertNanos > 300_000_000L) {
            plugin.alert(player, result.check(), vl, result.detail());
            data.lastAlertNanos = now;
        }
        if (MovementCheckSupport.isMovementCheck(result.check())
                && vl >= Math.max(1.0, plugin.settings().setbackViolations())) {
            scheduleMovementCorrection(player, data);
        }
    }

    public void scheduleMovementCorrection(Player player, PlayerData data) {
        scheduleMovementCorrection(player, data, true);
    }

    public void scheduleMovementCorrection(Player player, PlayerData data, boolean alert) {
        Vec3 position = data.lastVerifiedPosition;
        if (position == null) return;
        if (!data.beginMovementCorrection()) return;
        directTeleportSetback(player, data, position.x(),
                position.y() - MovementConstants.CORRECTION_HEIGHT_OFFSET, position.z(),
                data.predictedOnGround,
                "position=" + position + " tick=" + Math.max(0, data.lastTick), alert);
    }

    public void trackGlideBoost(Player player, PlayerData data, InventoryTransactionPacket packet) {
        if (!data.motion.gliding()
                || !(packet.getTransaction() instanceof ItemUseInventoryTransaction transaction)
                || transaction.getActionType() != ItemUseActionType.USE) return;
        var item = player.getInventory().getItemInMainHand();
        if (item != null && ItemID.FIREWORK_ROCKET.equals(item.getId())) {
            data.motion.glideBoostTicks(MovementConstants.GLIDE_BOOST_TICKS);
        }
    }

    private void synchronizeMotionContext(Player player, PlayerData data) {
        int jumpBoostLevel = player.hasEffect(EffectType.JUMP_BOOST)
                ? player.getEffect(EffectType.JUMP_BOOST).getLevel() : 0;
        if (jumpBoostLevel != data.motion.jumpBoostLevel()) {
            data.motion.jumpBoostLevel(jumpBoostLevel);
        }
        float serverMovementSpeed = player.getMovementSpeed();
        if (Float.isFinite(serverMovementSpeed)
                && Math.abs(serverMovementSpeed - data.motion.movementSpeed()) > 1.0E-6f) {
            data.motion.movementSpeed(serverMovementSpeed);
            float defaultMovementSpeed = player.getMovementSpeedDefault();
            if (Float.isFinite(defaultMovementSpeed) && defaultMovementSpeed > 0.0f) {
                data.motion.defaultMovementSpeed(defaultMovementSpeed);
            }
        }
        float width = (float) (Double.isFinite(data.clientWidth)
                ? data.clientWidth : player.getWidth());
        float height;
        if (data.clientSwimming) {
            height = player.getSwimmingHeight();
        } else if (data.predictedSneaking) {
            height = player.getSneakingHeight();
        } else if (data.clientCrawling) {
            height = player.getCrawlingHeight();
        } else {
            height = player.getHeight();
        }
        float scale = (float) (Double.isFinite(data.clientScale)
                ? data.clientScale : player.getScale());
        data.motion.size(width, height, scale);
        data.motion.wearingElytra(data.clientPlayer.wearingElytra());
        Item boots = player.getInventory().getBoots();
        data.motion.depthStrider(boots == null || boots.isNull()
                ? 0 : boots.getEnchantmentLevel(Enchantment.ID_WATER_WALKER));
        GameType gameType = data.clientPlayer.gameType();
        data.motion.supportedGameMode(gameType == GameType.SURVIVAL
                || gameType == GameType.ADVENTURE);
        data.motion.legacySlideOffset(false);
        data.motion.modernSprintTiming(true);
    }

    private static void updateClientPose(PlayerData data, MovementInputFrame input) {
        if (input.has(MovementInputFlag.START_SWIMMING)) {
            data.clientSwimming = true;
        } else if (input.has(MovementInputFlag.STOP_SWIMMING)) {
            data.clientSwimming = false;
        }
        if (input.has(MovementInputFlag.START_CRAWLING)) {
            data.clientCrawling = true;
        } else if (input.has(MovementInputFlag.STOP_CRAWLING)) {
            data.clientCrawling = false;
        }
        if (input.has(MovementInputFlag.START_SNEAKING)) {
            data.predictedSneaking = true;
        } else if (input.has(MovementInputFlag.STOP_SNEAKING)) {
            data.predictedSneaking = false;
        }
    }

    private void acceptPipelineState(PlayerData data, Vector3f position,
                                     Vector3f velocity, boolean onGround) {
        data.simulationMismatchFrames = 0;
        FloatVector feet = new FloatVector(position.getX(),
                position.getY() - MovementConstants.PLAYER_HEIGHT_OFFSET,
                position.getZ());
        if (!data.motion.initialized()) {
            data.motion.initialize(feet, onGround);
        } else {
            data.motion.onGround(onGround);
        }
        data.predictedVelocity = new Vec3(velocity.getX(), velocity.getY(), velocity.getZ());
        data.predictedOnGround = onGround;
        data.predictedHorizontalCollision = false;
        data.authoritativePosition = new Vec3(position.getX(), position.getY(), position.getZ());
        data.penetratedLastFrame = false;
        data.stuckInCollider = false;
        data.resetFall();
    }

    private void ensureMovementWorldFrame(Player player, PlayerData data, long tick,
                                          Vector3f position, Vector3f rotation) {
        if (data.history.latest() != null) {
            return;
        }
        data.history.capture(player, tick, new Vec3(position.getX(), position.getY(), position.getZ()),
                data.predictedVelocity, rotation.getY(), rotation.getX(), data.predictedOnGround,
                data.clientWorld, data.clientEntities);
    }

    private void captureMovementWorldFrame(Player player, PlayerData data, long tick,
                                           Vector3f position, Vector3f rotation) {
        data.history.capture(player, tick, new Vec3(position.getX(), position.getY(), position.getZ()),
                data.predictedVelocity, rotation.getY(), rotation.getX(),
                data.predictedOnGround, data.clientWorld, data.clientEntities);
    }

    private void sendSimulationCorrection(PacketReceiveEvent event, Player player, PlayerData data,
                                           MovementPipelineResult result) {
        event.setCancelled();
        if (!data.beginMovementCorrection()) {
            return;
        }
        FloatVector target = result.authoritativePosition();
        directTeleportSetback(player, data, target.x(), target.y(), target.z(),
                result.onGround(),
                "offset=" + NetworkCheckSupport.format(result.offset())
                        + " client=" + result.clientPosition()
                        + " predicted=" + result.authoritativePosition()
                        + " tick=" + result.tick());
    }

    private void inspectGroundSpoof(PacketReceiveEvent event, Player player, PlayerData data,
                                    Vector3f position) {
        if (data.inGrace() || player.getAllowFlight() || player.isFlying()
                || player.isSpectator() || player.getRiding() != null
                || !data.motion.client().verticalCollision()
                || data.motion.wearingElytra()
                || MovementCheckSupport.serverGround(player, position)
                || MovementCheckSupport.nearPartialBlock(player, position)) {
            data.groundSpoofBuffer = 0;
            return;
        }

        data.groundSpoofBuffer++;
        if (data.groundSpoofBuffer < GROUND_SPOOF_TICKS) {
            return;
        }

        data.groundSpoofBuffer = 0;
        violations.fail(event, player, data, CheckType.GROUND_SPOOF_A, 1,
                "position=" + position, false, false);
    }

    private void inspectCobweb(PacketReceiveEvent event, Player player, PlayerData data,
                               Vector3f position, Vec3 movement) {
        if (data.inGrace() || player.getAllowFlight() || player.isFlying() || player.isSpectator()
                || player.getRiding() != null || data.hasMovementCorrection()
                || data.hasPendingTeleport()
                || !MovementCheckSupport.insideCobweb(player, position)) {
            data.cobwebBuffer = 0;
            return;
        }

        double horizontal = Math.sqrt(movement.x() * movement.x() + movement.z() * movement.z());
        double allowed = COBWEB_MULTIPLIER * (Math.max(DEFAULT_MOVEMENT_SPEED,
                player.getMovementSpeed()) * COBWEB_SPEED_ALLOWANCE + COBWEB_JUMP_ALLOWANCE);
        if (horizontal <= allowed) {
            data.cobwebBuffer = 0;
            return;
        }

        data.cobwebBuffer++;
        if (data.cobwebBuffer < COBWEB_TICKS) {
            return;
        }

        data.cobwebBuffer = 0;
        violations.fail(event, player, data, CheckType.COBWEB_A, 1,
                "moved " + NetworkCheckSupport.format(horizontal) + " of "
                        + NetworkCheckSupport.format(allowed), false, true);
    }

    private void inspectSprint(PacketReceiveEvent event, Player player, PlayerData data) {
        boolean sprinting = data.motion.sprinting();
        boolean startedSprinting = sprinting && !data.wasSprinting;
        data.wasSprinting = sprinting;

        if (data.inGrace() || player.getAllowFlight() || player.isFlying() || player.isSpectator()
                || player.getRiding() != null || !sprinting) {
            data.sprintFoodBuffer = 0;
            data.sprintUseBuffer = 0;
            return;
        }

        if (!data.motion.wearingElytra()
                && player.getFoodData().getFood() <= SPRINT_MINIMUM_FOOD) {
            if (++data.sprintFoodBuffer >= SPRINT_TICKS) {
                data.sprintFoodBuffer = 0;
                violations.fail(event, player, data, CheckType.SPRINT_A, 1,
                        "food=" + player.getFoodData().getFood(), false, true);
            }
        } else {
            data.sprintFoodBuffer = 0;
        }

        if (data.motion.consuming()) {
            if (++data.sprintUseBuffer >= SPRINT_TICKS) {
                data.sprintUseBuffer = 0;
                violations.fail(event, player, data, CheckType.SPRINT_B, 1, "using an item", false, true);
            }
        } else {
            data.sprintUseBuffer = 0;
        }

        if (startedSprinting && player.hasEffect(EffectType.BLINDNESS)) {
            violations.fail(event, player, data, CheckType.SPRINT_C, 1, "blinded", false, true);
        }
    }

    private void trackLevitation(Player player, PlayerData data) {
        boolean levitating = player.hasEffect(EffectType.LEVITATION);
        if (data.wasLevitating && !levitating) {
            data.grantGrace(GraceReason.EFFECT_CHANGE, LEVITATION_GRACE_MILLIS);
        }
        data.wasLevitating = levitating;
    }

    private void inspectElytra(PacketReceiveEvent event, Player player, PlayerData data,
                               PlayerAuthInputPacket packet) {
        var input = packet.getInputData();
        if (!input.contains(PlayerAuthInputData.START_GLIDING)) {
            return;
        }

        long previousStart = data.lastGlideStartTick;
        data.lastGlideStartTick = data.lastTick;
        if (data.inGrace() || player.isSpectator()) {
            return;
        }

        if (player.getRiding() != null) {
            violations.fail(event, player, data, CheckType.ELYTRA_A, 1, "riding", false, true);
        }
        if (previousStart != Long.MIN_VALUE) {
            long since = data.lastTick - previousStart;
            if (since < ELYTRA_START_TICKS) {
                violations.fail(event, player, data, CheckType.ELYTRA_B, 1,
                        "restarted after " + since + " ticks", false, true);
            }
        }
    }

    private void measureMeleeKnockback(PacketReceiveEvent event, Player player, PlayerData data,
                                       MovementPipelineResult result, Vec3 observedMovement) {
        if (data.meleeKnockbackTicks <= 0 || data.expectedMeleeKnockback == null) {
            return;
        }

        if (!result.reliable() || data.hasMovementCorrection() || data.hasPendingTeleport()) {
            data.meleeKnockbackTicks = 0;
            data.expectedMeleeKnockback = null;
            return;
        }

        double previousX = result.clientPosition().x() - observedMovement.x();
        double previousZ = result.clientPosition().z() - observedMovement.z();
        double predictedX = result.authoritativePosition().x() - previousX;
        double predictedZ = result.authoritativePosition().z() - previousZ;

        data.meleeKnockbackTicks--;
        data.meleeKnockbackExpected += Math.sqrt(predictedX * predictedX + predictedZ * predictedZ);
        data.meleeKnockbackObserved += Math.sqrt(observedMovement.x() * observedMovement.x()
                + observedMovement.z() * observedMovement.z());
        if (data.meleeKnockbackTicks > 0) {
            return;
        }

        Vec3 expected = data.expectedMeleeKnockback;
        data.expectedMeleeKnockback = null;

        double horizontal = Math.sqrt(expected.x() * expected.x() + expected.z() * expected.z());
        double travelled = data.meleeKnockbackExpected;
        if (travelled < 0.3) {
            return;
        }

        double ratio = data.meleeKnockbackObserved / travelled;
        if (ratio >= VELOCITY_MINIMUM_RATIO && ratio <= VELOCITY_MAXIMUM_RATIO) {
            data.velocityBuffer = Math.max(0.0, data.velocityBuffer - 1.0);
            return;
        }

        if (ratio < VELOCITY_MINIMUM_RATIO) {
            reapplyMissingKnockback(player, data, expected, horizontal,
                    travelled - data.meleeKnockbackObserved);
        }
        data.velocityBuffer++;
        if (data.velocityBuffer < VELOCITY_BUFFER) {
            return;
        }

        data.velocityBuffer = 0.0;
        violations.fail(event, player, data, CheckType.VELOCITY_A, 1,
                "ratio=" + NetworkCheckSupport.format(ratio)
                        + " expected=" + NetworkCheckSupport.format(travelled)
                        + " observed=" + NetworkCheckSupport.format(data.meleeKnockbackObserved),
                false, false);
    }

    private void reapplyMissingKnockback(Player player, PlayerData data, Vec3 expected,
                                         double horizontal, double missingDistance) {
        if (horizontal < 1.0E-4 || missingDistance < VELOCITY_MINIMUM_PUSH) {
            return;
        }

        double push = Math.min(missingDistance, VELOCITY_MAXIMUM_PUSH);
        double x = player.getX() + expected.x() / horizontal * push;
        double z = player.getZ() + expected.z() / horizontal * push;
        directTeleportSetback(player, data, x, player.getY(), z, player.isOnGround(),
                "knockback push=" + NetworkCheckSupport.format(push), false);
    }

    private void accumulateSimulationOffset(PacketReceiveEvent event, Player player,
                                            PlayerData data, MovementPipelineResult result) {
        if (!result.anchored() || data.inGrace() || data.hasMovementCorrection()
                || data.hasPendingTeleport() || beyondSimulatedSpeed(player)) {
            return;
        }
        if (data.movementPacketDropped) {
            data.movementPacketDropped = false;
            return;
        }

        if (result.offset() > DESYNC_OFFSET) {
            data.simulationOffsetBuffer = 0.0;
            return;
        }

        double tolerance = plugin.settings().predictionTolerance();
        tolerance *= Math.max(1.0, player.getMovementSpeed() / DEFAULT_MOVEMENT_SPEED);
        if (result.inFluid()) {
            tolerance *= FLUID_TOLERANCE;
        }
        if (recentlyGliding(data)) {
            tolerance *= GLIDE_TOLERANCE;
        }
        if (data.nearServerMotionTick) {
            tolerance *= 4.0;
        }
        if (result.ticksSinceImpulse() < IMPULSE_TOLERANCE_TICKS) {
            tolerance *= 3.0;
        }

        double excess = Math.max(0.0, result.offset() - tolerance);
        data.simulationOffsetBuffer = Math.max(0.0, data.simulationOffsetBuffer + excess
                - plugin.settings().predictionBufferDecay());

        if (data.simulationOffsetBuffer <= 0.0 && result.onGround()) {
            data.lastVerifiedPosition = new Vec3(result.clientPosition().x(),
                    result.clientPosition().y() + MovementConstants.CORRECTION_HEIGHT_OFFSET,
                    result.clientPosition().z());
        }

        double threshold = Math.max(0.05, plugin.settings().predictionBufferThreshold());
        if (data.simulationOffsetBuffer < threshold) {
            return;
        }

        data.simulationOffsetBuffer = 0.0;
        violations.fail(event, player, data, CheckType.SIMULATION, 1,
                "offset=" + NetworkCheckSupport.format((float) result.offset())
                        + " client=" + result.clientPosition()
                        + " predicted=" + result.authoritativePosition()
                        + " ground=" + result.onGround()
                        + " support=" + result.supportingBlock() + "@" + result.supportingBlockY()
                        + " below=" + result.blockBelow()
                        + " vy=" + NetworkCheckSupport.format((float) result.authoritativeVelocity().y())
                        + (result.inFluid() ? " fluid" : "")
                        + (result.impulseApplied() ? " kb-applied" : "")
                        + (result.impulseDeferred() ? " kb-deferred" : "")
                        + (result.ticksSinceImpulse() < 40 ? " kb-age=" + result.ticksSinceImpulse() : "")
                        + (data.nearServerMotionTick ? " impulse" : "")
                        + " tick=" + result.tick(), false, false);

        double vls = data.violations.getOrDefault(CheckType.SIMULATION.id(), 0.0);
        if (vls >= Math.max(1.0, plugin.settings().setbackViolations())) {
            scheduleMovementCorrection(player, data);
        }
    }

    private void inspectVehicleMovement(PacketReceiveEvent event, Player player, PlayerData data,
                                        PlayerAuthInputPacket packet, long tick, long tickDelta,
                                        Vector3f position, Vector3f rotation) {
        Entity vehicle = player.getRiding();
        Long predictedId = packet.getClientPredictedVehicle();
        if (data.vehicleId != vehicle.getId()) {
            data.resetVehicle();
            data.vehicleId = vehicle.getId();
            data.lastTick = tick;
            data.predictedVehicleVelocity = new Vec3(vehicle.motionX, vehicle.motionY, vehicle.motionZ);
            data.resetFall();
            return;
        }

        boolean clientPredictingVehicle = packet.getInputData()
                .contains(PlayerAuthInputData.IS_IN_CLIENT_PREDICTED_VEHICLE)
                && predictedId != null && predictedId != 0;
        if (!clientPredictingVehicle) {
            data.vehicleInputConfirmed = false;
            data.vehicleWarmupPackets = 0;
            data.lastTick = tick;
            data.resetFall();
            return;
        }
        if (predictedId != vehicle.getId()) {
            violations.fail(event, player, data, CheckType.BAD_PACKET_C, 2, "vehicle-id=" + predictedId, true, true);
            return;
        }
        if (!data.vehicleInputConfirmed) {
            data.vehicleInputConfirmed = true;
            data.vehicleWarmupPackets = 5;
            data.lastPosition = position;
            data.lastTick = tick;
            data.predictedVehicleVelocity = new Vec3(vehicle.motionX, vehicle.motionY, vehicle.motionZ);
            data.resetFall();
            return;
        }
        if (data.vehicleWarmupPackets > 0) {
            Vec3 target = VehiclePositionSupport.fromPacket(vehicle, player, position);
            Vec3 previous = VehiclePositionSupport.fromPacket(vehicle, player, data.lastPosition);
            data.predictedVehicleVelocity = target.add(-previous.x(), -previous.y(), -previous.z());
            data.vehicleWarmupPackets--;
            data.lastPosition = position;
            data.lastTick = tick;
            data.resetFall();
            return;
        }

        VehiclePredictionResult result = vehiclePredictor.predict(player, data, packet, tickDelta);
        var settings = plugin.settings();
        double toleranceVal = settings.vehicleTolerance();
        if (result.offset() > toleranceVal) {
            data.vehicleBuffer += Math.min(result.offset() - toleranceVal, 0.75);
            double thresholdVal = settings.vehicleBufferThreshold();
            if (data.vehicleBuffer > thresholdVal) {
                violations.fail(event, player, data, CheckType.VEHICLE_A, 1,
                        "type=" + result.type() + " offset=" + NetworkCheckSupport.format(result.offset()),
                        true, false);
                scheduleVehicleCorrection(player, data, vehicle);
                data.vehicleBuffer = thresholdVal * 0.5;
                return;
            }
        } else {
            if (!data.hasMovementCorrection() && !data.hasPendingTeleport()) {
                data.finishSimulationCorrectionEpisode();
            }
            data.vehicleBuffer = Math.max(0, data.vehicleBuffer
                    - settings.vehicleBufferDecay());
            if (data.vehicleBuffer <= 0.0) {
                data.lastVerifiedVehiclePosition = new Vec3(vehicle.x, vehicle.y, vehicle.z);
            }
        }

        data.predictedVehicleVelocity = result.velocity();
        data.predictedVelocity = result.velocity();
        data.predictedOnGround = vehicle.isOnGround();
        data.lastPosition = position;
        data.lastTick = tick;
        data.resetFall();
        if (!data.inGrace()) {
            var combatRotation = packet.getInteractRotation();
            float combatYaw = combatRotation == null || !Float.isFinite(combatRotation.getY())
                    ? rotation.getY() : combatRotation.getY();
            float combatPitch = combatRotation == null || !Float.isFinite(combatRotation.getX())
                    ? rotation.getX() : combatRotation.getX();
            data.history.capture(player, tick, new Vec3(position.getX(), position.getY(), position.getZ()),
                    result.velocity(), combatYaw, combatPitch, vehicle.isOnGround(), data.clientWorld,
                    data.clientEntities);
        }
    }

    private boolean validatePendingFall(PacketReceiveEvent event, Player player, PlayerData data, long tick) {
        if (data.pendingFallTick < 0 || tick <= data.pendingFallTick
                || data.inputSequence - data.pendingFallInputSequence < 3
                || System.nanoTime() < data.pendingFallDeadlineNanos) return false;
        boolean serverExpectsDamage = data.pendingFallDamage > 0
                && data.fallEventObserved
                && !data.fallEventCancelled
                && data.observedServerFallDistance > 3.255;
        boolean invalid = serverExpectsDamage && !data.fallDamageObserved;
        String detail = "distance=" + NetworkCheckSupport.format(data.pendingFallDistance)
                + " expected-damage=" + NetworkCheckSupport.format(data.pendingFallDamage)
                + " received-damage=" + NetworkCheckSupport.format(data.observedFallDamage);
        data.resetFall();
        if (!invalid) {
            data.noFallBuffer = Math.max(0, data.noFallBuffer - 1);
            return false;
        }
        if (++data.noFallBuffer < 2) return false;
        data.noFallBuffer = 1;
        violations.fail(event, player, data, CheckType.NO_FALL_A, 1, detail, true, true);
        return true;
    }

    private static void updateFallPrediction(Player player, PlayerData data, Vector3f position,
                                             boolean previousGround, boolean onGround, long tick) {
        if (fallResetEnvironment(player, position)) {
            data.resetFall();
            return;
        }
        double deltaY = data.lastPosition == null ? 0 : position.getY() - data.lastPosition.getY();
        if (deltaY > 0) data.simulatedFallDistance = 0;
        else if (!onGround && deltaY < 0) data.simulatedFallDistance += -deltaY;

        if (!previousGround && onGround && data.simulatedFallDistance > 3.255) {
            double distance = data.simulatedFallDistance;
            int jumpLevel = player.hasEffect(EffectType.JUMP_BOOST)
                    ? player.getEffect(EffectType.JUMP_BOOST).getLevel() : 0;
            int feetY = (int) Math.floor(position.getY() - player.getBaseOffset());
            var below = player.getLevel().getBlock((int) Math.floor(position.getX()), feetY - 1,
                    (int) Math.floor(position.getZ()));
            boolean fallDamage = player.getLevel().getGameRules().getBoolean(GameRule.FALL_DAMAGE)
                    && below.useDefaultFallDamage();
            data.pendingFallDamage = fallDamage ? Math.max(0, distance - 3.255 - jumpLevel) : 0;
            if (data.pendingFallDamage <= 0) {
                data.resetFall();
                return;
            }
            data.pendingFallDistance = distance;
            data.pendingFallTick = tick;
            data.pendingFallInputSequence = data.inputSequence;
            data.pendingFallDeadlineNanos = System.nanoTime() + 750_000_000L;
            data.fallEventObserved = false;
            data.fallEventCancelled = false;
            data.observedServerFallDistance = 0;
            data.fallDamageObserved = false;
            data.observedFallDamage = 0;
        }
        if (onGround) data.simulatedFallDistance = 0;
    }

    private static boolean fallResetEnvironment(Player player, Vector3f position) {
        if (player.isGliding() || player.hasEffect(EffectType.SLOW_FALLING)) return true;
        int x = (int) Math.floor(position.getX());
        int feetY = (int) Math.floor(position.getY() - player.getBaseOffset());
        int z = (int) Math.floor(position.getZ());
        var inside = player.getLevel().getBlock(x, feetY, z);
        var below = player.getLevel().getBlock(x, feetY - 1, z);
        String insideId = inside.getId();
        String belowId = below.getId();
        return player.isTouchingWater() || player.isInsideOfWater() || player.isInsideOfLava()
                || insideId.contains("water") || insideId.contains("lava") || inside.canBeClimbed()
                || insideId.contains("web") || insideId.contains("scaffolding")
                || insideId.contains("powder_snow") || belowId.contains("slime")
                || insideId.contains("fence") || belowId.contains("fence");
    }

    private void applyBurstCandidates(PlayerData data, Vec3 observedMovement) {
        Vec3 pending = data.motion.hasKnockback()
                ? new Vec3(data.motion.knockback().x(), data.motion.knockback().y(),
                        data.motion.knockback().z())
                : null;

        Vec3 best = null;
        double bestDistance = BURST_MATCH_DISTANCE;

        List<Vec3> candidates = new ArrayList<>(data.windChargeCandidates());
        Vec3 lunge = data.spearLungeCandidate();
        if (lunge != null) {
            candidates.add(lunge);
        }

        for (Vec3 candidate : candidates) {
            double distance = candidate.distance(observedMovement);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
            if (pending == null) {
                continue;
            }
            Vec3 combined = candidate.add(pending.x(), pending.y(), pending.z());
            double combinedDistance = combined.distance(observedMovement);
            if (combinedDistance < bestDistance) {
                bestDistance = combinedDistance;
                best = combined;
            }
        }

        if (best == null) {
            return;
        }

        data.motion.knockback(new FloatVector((float) best.x(), (float) best.y(),
                (float) best.z()));
        data.clearWindChargeCandidates();
        data.clearSpearLungeCandidate();
    }

    private void scheduleVehicleCorrection(Player player, PlayerData data, Entity vehicle) {
        Vec3 target = data.lastVerifiedVehiclePosition;
        if (target == null || !data.beginMovementCorrection()) {
            return;
        }

        plugin.getServer().getScheduler().scheduleTask(plugin, () -> {
            data.finishMovementCorrection();
            if (!player.isOnline() || vehicle.isClosed() || vehicle.getLevel() == null) {
                return;
            }
            vehicle.setMotion(new Vector3(0, 0, 0));
            vehicle.teleport(new Location(target.x(), target.y(), target.z(),
                    vehicle.yaw, vehicle.pitch, vehicle.getLevel()));
            data.predictedVehicleVelocity = Vec3.ZERO;
            data.vehicleWarmupPackets = 5;
        });
    }

    private void directTeleportSetback(Player player, PlayerData data,
                                       double x, double y, double z, boolean onGround,
                                       String detail) {
        directTeleportSetback(player, data, x, y, z, onGround, detail, true);
    }

    private void directTeleportSetback(Player player, PlayerData data,
                                       double x, double y, double z, boolean onGround,
                                       String detail, boolean alert) {
        Runnable send = () -> {
            if (!player.isOnline()) {
                data.finishMovementCorrection();
                data.finishSimulationCorrectionEpisode();
                return;
            }
            Location current = player.getLocation();
            Location target = new Location(x, y, z, current.yaw, current.pitch,
                    current.headYaw, player.getLevel());

            data.resetFall();
            data.clearVelocities();
            data.motion.clearTransientMotion();
            data.motion.onGround(onGround);
            data.predictedVelocity = Vec3.ZERO;
            data.predictedOnGround = onGround;
            data.predictedHorizontalCollision = false;
            data.penetratedLastFrame = false;
            data.stuckInCollider = false;
            data.simulationMismatchFrames = 0;
            data.setbackTeleportPending = true;
            data.stageDirectSetback(Vector3f.from(x,
                    y + MovementConstants.CORRECTION_HEIGHT_OFFSET, z), onGround);

            data.finishMovementCorrection();
            if (!player.teleport(target, PlayerTeleportEvent.TeleportCause.PLUGIN)) {
                data.setbackTeleportPending = false;
                data.clearDirectSetback();
                data.finishSimulationCorrectionEpisode();
                return;
            }
            data.safeLocation = player.getLocation();
            if (alert && data.beginSimulationCorrectionEpisode()) {
                double vl = data.violations.merge(CheckType.SIMULATION.id(), 1.0, Double::sum);
                plugin.alert(player, CheckType.SIMULATION, vl, detail);
                data.lastAlertNanos = System.nanoTime();
            }
        };
        if (plugin.getServer().isPrimaryThread()) {
            send.run();
        } else {
            plugin.getServer().getScheduler().scheduleTask(plugin, send);
        }
    }

    public static boolean recentlySleeping(PlayerData data) {
        return data.lastSleepingTick != Long.MIN_VALUE
                && data.lastTick - data.lastSleepingTick < SLEEP_GRACE_TICKS;
    }

    private static boolean recentlyGliding(PlayerData data) {
        return data.lastGlideTick != Long.MIN_VALUE
                && data.lastTick - data.lastGlideTick < GLIDE_TAIL_TICKS;
    }

    private static boolean beyondSimulatedSpeed(Player player) {
        return player.getMovementSpeed() > MAX_SIMULATED_MOVEMENT_SPEED;
    }
}
