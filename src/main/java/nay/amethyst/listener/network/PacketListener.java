package nay.amethyst.listener.network;

import nay.amethyst.AmethystPlugin;
import nay.amethyst.check.inventory.InventoryMoveCheck;
import nay.amethyst.check.packet.BadPacketCheck;
import nay.amethyst.check.type.CheckType;
import nay.amethyst.data.player.PlayerData;
import nay.amethyst.data.player.VelocityImpulse;
import nay.amethyst.prediction.combat.CombatPredictionResult;
import nay.amethyst.prediction.combat.CombatPredictor;
import nay.amethyst.prediction.common.Vec3;
import nay.amethyst.prediction.movement.BedrockMath;
import nay.amethyst.prediction.vehicle.VehiclePredictionResult;
import nay.amethyst.prediction.vehicle.VehiclePredictor;
import nay.amethyst.tracking.entity.ClientEntityTracker;
import nay.amethyst.tracking.network.ack.AcknowledgmentType;
import nay.amethyst.history.model.BlockFrame;
import nay.amethyst.tracking.world.ClientWorldTracker;
import nay.amethyst.listener.network.support.MovementCheckSupport;
import nay.amethyst.listener.network.support.NetworkCheckSupport;
import nay.amethyst.listener.network.support.VehiclePositionSupport;
import nay.amethyst.network.session.MovementSessionRegistry;
import nay.amethyst.packet.movement.MovementPreValidationResult;
import nay.amethyst.packet.movement.MovementInputAdapter;
import nay.amethyst.simulation.movement.FloatVector;
import nay.amethyst.simulation.movement.FrameWorldView;
import nay.amethyst.simulation.movement.MovementConstants;
import nay.amethyst.simulation.movement.MovementInputFlag;
import nay.amethyst.simulation.movement.MovementInputFrame;
import nay.amethyst.simulation.movement.MovementPipelineResult;
import org.cloudburstmc.math.vector.Vector2f;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.PlayerActionType;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.data.AbilitiesIndex;
import org.cloudburstmc.protocol.bedrock.data.actor.ActorDataTypes;
import org.cloudburstmc.protocol.bedrock.data.actor.ActorDataMap;
import org.cloudburstmc.protocol.bedrock.data.actor.ActorFlags;
import org.cloudburstmc.protocol.bedrock.data.PlayerBlockActionData;
import org.cloudburstmc.protocol.bedrock.data.BuildPlatform;
import org.cloudburstmc.protocol.bedrock.data.InputMode;
import org.cloudburstmc.protocol.bedrock.data.GameType;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerId;
import org.cloudburstmc.protocol.bedrock.data.PredictionType;
import org.cloudburstmc.protocol.bedrock.data.payload.move.PositionMode;
import org.cloudburstmc.protocol.bedrock.data.payload.inventory.transaction.ItemUseActionType;
import org.cloudburstmc.protocol.bedrock.data.payload.inventory.transaction.ItemUseOnActorActionType;
import org.cloudburstmc.protocol.bedrock.data.payload.inventory.transaction.data.ItemUseInventoryTransaction;
import org.cloudburstmc.protocol.bedrock.data.payload.inventory.transaction.data.ItemReleaseInventoryTransaction;
import org.cloudburstmc.protocol.bedrock.data.payload.inventory.transaction.data.ItemUseOnActorInventoryTransaction;
import org.cloudburstmc.protocol.bedrock.packet.InventoryTransactionPacket;
import org.cloudburstmc.protocol.bedrock.packet.InventoryContentPacket;
import org.cloudburstmc.protocol.bedrock.packet.InventorySlotPacket;
import org.cloudburstmc.protocol.bedrock.packet.ItemStackRequestPacket;
import org.cloudburstmc.protocol.bedrock.packet.AddActorPacket;
import org.cloudburstmc.protocol.bedrock.packet.AddPlayerPacket;
import org.cloudburstmc.protocol.bedrock.packet.CorrectPlayerMovePredictionPacket;
import org.cloudburstmc.protocol.bedrock.packet.MoveActorAbsolutePacket;
import org.cloudburstmc.protocol.bedrock.packet.MoveActorDeltaPacket;
import org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket;
import org.cloudburstmc.protocol.bedrock.packet.MobEffectPacket;
import org.cloudburstmc.protocol.bedrock.packet.LevelChunkPacket;
import org.cloudburstmc.protocol.bedrock.packet.SubChunkPacket;
import org.cloudburstmc.protocol.bedrock.packet.NetworkStackLatencyPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;
import org.cloudburstmc.protocol.bedrock.packet.RemoveActorPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetActorMotionPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetActorDataPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetPlayerGameTypePacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateBlockPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateAttributesPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateAbilitiesPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateSubChunkBlocksPacket;
import org.powernukkitx.Player;
import org.powernukkitx.block.Block;
import org.powernukkitx.block.BlockID;
import org.powernukkitx.event.block.BlockBreakEvent;
import org.powernukkitx.event.block.BlockPlaceEvent;
import org.powernukkitx.entity.Entity;
import org.powernukkitx.entity.projectile.EntityWindCharge;
import org.powernukkitx.entity.effect.EffectType;
import org.powernukkitx.event.EventHandler;
import org.powernukkitx.event.EventPriority;
import org.powernukkitx.event.Listener;
import org.powernukkitx.event.entity.EntityDamageEvent;
import org.powernukkitx.event.entity.EntityDamageByEntityEvent;
import org.powernukkitx.event.entity.EntityFallEvent;
import org.powernukkitx.event.entity.EntityMotionEvent;
import org.powernukkitx.event.entity.ProjectileHitEvent;
import org.powernukkitx.event.player.PlayerJoinEvent;
import org.powernukkitx.event.player.PlayerLoginEvent;
import org.powernukkitx.event.player.PlayerQuitEvent;
import org.powernukkitx.event.player.PlayerRespawnEvent;
import org.powernukkitx.event.player.PlayerSpearStabEvent;
import org.powernukkitx.event.player.PlayerTeleportEvent;
import org.powernukkitx.event.server.PacketReceiveEvent;
import org.powernukkitx.event.server.PacketSendEvent;
import org.powernukkitx.level.Location;
import org.powernukkitx.level.GameRule;
import org.powernukkitx.math.Vector3;
import org.powernukkitx.item.ItemID;
import org.powernukkitx.item.Item;
import org.powernukkitx.item.ItemSpear;
import org.powernukkitx.item.enchantment.Enchantment;
import org.powernukkitx.registry.Registries;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

public final class PacketListener implements Listener {
    private static final double BURST_MATCH_DISTANCE = 0.35;
    /** Join and respawn drop the player in mid-air before the first input arrives. */
    private static final long GRACE_MILLIS = 2000;
    /** A spoofed client tick is cheap to send, so it is only worth kicking once it is clearly repeated. */
    private static final double BAD_PACKET_D_KICK_VIOLATIONS = 35.0;
    /** A teleport can drop the player somewhere they then fall from, which outlasts the usual grace. */
    private static final long TELEPORT_GRACE_MILLIS = 3000;
    /** Beyond this, one tick of movement cannot be the explanation. */
    private static final double DESYNC_OFFSET = 8.0;

    private final AmethystPlugin plugin;
    private final Map<UUID, PlayerData> players;
    private final MovementSessionRegistry movementSessions;
    private final VehiclePredictor vehiclePredictor = new VehiclePredictor();
    private final CombatPredictor combatPredictor = new CombatPredictor();
    private final InventoryMoveCheck inventoryMoveCheck = new InventoryMoveCheck();
    private final BadPacketCheck badPacketCheck = new BadPacketCheck();

    public PacketListener(AmethystPlugin plugin, Map<UUID, PlayerData> players,
                          MovementSessionRegistry movementSessions) {
        this.plugin = plugin;
        this.players = players;
        this.movementSessions = movementSessions;
    }

    public void onServerTick() {
        if (players.isEmpty()) return;
        long now = System.nanoTime();
        for (Player player : plugin.getServer().getOnlinePlayers().values()) {
            PlayerData data = players.get(player.getUniqueId());
            if (data == null || !data.joined) continue;
            data.network.tick(now);
            if (data.network.shouldProbe()) sendAcknowledgment(player, data, null);
        }
    }

    public nay.amethyst.tracking.network.NetworkTimeline networkTimeline(Player player) {
        PlayerData data = players.get(player.getUniqueId());
        return data == null || !data.joined ? null : data.network;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLogin(PlayerLoginEvent event) {
        Player player = event.getPlayer();
        data(player);
        movementSessions.install(player);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerData data = data(player);
        data.joined = true;
        data.safeLocation = player.getLocation();
        var chain = player.getClientChainData();
        if (chain != null) {
            BuildPlatform platform = chain.getDeviceOS();
            boolean mobile = platform == BuildPlatform.GOOGLE || platform == BuildPlatform.IOS
                    || platform == BuildPlatform.AMAZON;
            data.touchInput = mobile && chain.getCurrentInputMode() == InputMode.TOUCH;
            data.modernItemUseProtocol = NetworkCheckSupport.versionAtLeast(chain.getGameVersion(), 1, 21, 20);
            data.network.setSonyClient(platform == BuildPlatform.SONY);
        }
        data.grantGrace(GRACE_MILLIS);
        movementSessions.reset(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        movementSessions.restore(event.getPlayer());
        players.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        PlayerData data = players.get(event.getPlayer().getUniqueId());
        if (data == null) {
            return;
        }
        data.safeLocation = event.getRespawnPosition().left().getLocation();
        data.lastPosition = null;
        data.clientPlayer.ready(false);
        data.grantGrace(GRACE_MILLIS);
        movementSessions.reset(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        PlayerData data = players.get(event.getPlayer().getUniqueId());
        if (data == null) {
            return;
        }
        if (data.setbackTeleportPending) {
            data.setbackTeleportPending = false;
            Location to = event.getTo();
            synchronizeCorrection(data, Vector3f.from(to.x, to.y + event.getPlayer().getBaseOffset(), to.z));
            return;
        }
        // Someone else moved the player. The simulation is still standing where they were, and the
        // packets already in flight still report the old position, so it has to be rebuilt at the
        // destination instead of measuring the distance between the two.
        data.safeLocation = event.getTo();
        data.lastPosition = null;
        data.grantGrace(TELEPORT_GRACE_MILLIS);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        applyLocalBlock(event.getPlayer(), event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block broken = event.getBlock();
        applyLocalBlock(event.getPlayer(), Block.get(BlockID.AIR, broken.getLevel(),
                broken.getFloorX(), broken.getFloorY(), broken.getFloorZ()));
    }

    private void applyLocalBlock(Player player, Block block) {
        if (player == null || block == null) {
            return;
        }
        PlayerData data = players.get(player.getUniqueId());
        if (data == null || !data.joined) {
            return;
        }
        data.clientWorld.applyLocal(block.getFloorX(), block.getFloorY(), block.getFloorZ(), 0,
                BlockFrame.capture(block));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onReceive(PacketReceiveEvent event) {
        Player player = event.getPlayer();
        if (player == null || player.hasPermission("amethyst.bypass")) return;
        PlayerData playerData = players.get(player.getUniqueId());
        if (playerData == null || !playerData.joined) return;
        if (event.getPacket() instanceof NetworkStackLatencyPacket packet) {
            runAcknowledgments(playerData.network.acknowledge(packet.getCreationTime(), System.nanoTime()));
            return;
        }
        BadPacketCheck.Result badPacket = badPacketCheck.inspect(player, playerData, event.getPacket());
        if (badPacket != null) {
            if (event.getPacket() instanceof PlayerAuthInputPacket) {
                playerData.movementPacketDropped = true;
            }
            fail(event, player, playerData, badPacket.check(), 2, badPacket.detail(), true);
            if (badPacket.check() == CheckType.BAD_PACKET_E) {
                kick(player, playerData);
            } else if (badPacket.check() == CheckType.BAD_PACKET_D
                    && playerData.violations.getOrDefault(CheckType.BAD_PACKET_D.id(), 0.0)
                            >= BAD_PACKET_D_KICK_VIOLATIONS) {
                kick(player, playerData);
            }
            return;
        }
        if (event.getPacket() instanceof PlayerAuthInputPacket packet) {
            if (packet.getClientTick() > playerData.lastTick) playerData.clientEntities.tick();
            inspectInventoryMove(event, player, playerData, packet);
            inspectMovement(event, player, packet);
        } else if (event.getPacket() instanceof ItemStackRequestPacket packet) {
            inventoryMoveCheck.handleRequest(playerData, packet, System.nanoTime());
        } else if (event.getPacket() instanceof InventoryTransactionPacket packet) {
            trackItemUseState(player, playerData, packet);
            trackGlideBoost(player, playerData, packet);
            inspectCombat(event, player, packet);
        }
    }

    private void inspectInventoryMove(PacketReceiveEvent event, Player player, PlayerData data,
                                      PlayerAuthInputPacket packet) {
        var settings = plugin.settings();
        double threshold = Math.max(0.02, settings.inventoryMoveInputThreshold());
        long window = Math.max(100, settings.inventoryMoveRequestWindowMs());
        int buffer = Math.max(2, settings.inventoryMoveBufferThreshold());
        InventoryMoveCheck.Result result = inventoryMoveCheck.handleMovement(
                data, packet, System.nanoTime(), threshold, window, buffer);
        if (result.failed()) {
            fail(event, player, data, CheckType.INV_MOVE_A, 1,
                    "input=" + NetworkCheckSupport.format(result.inputMagnitude()), false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSend(PacketSendEvent event) {
        if (players.isEmpty()) return;
        Player player = event.getPlayer();
        if (player == null) return;
        PlayerData data = players.get(player.getUniqueId());
        if (data == null) return;
        if (event.getPacket() instanceof LevelChunkPacket
                || event.getPacket() instanceof SubChunkPacket) {
            sendAcknowledgmentAfter(event, player, data, AcknowledgmentType.INITIALIZATION,
                    () -> data.clientPlayer.ready(true));
            return;
        }
        if (event.getPacket() instanceof SetPlayerGameTypePacket packet) {
            var gameType = packet.getPlayerGameType();
            sendAcknowledgmentAfter(event, player, data, AcknowledgmentType.GAME_MODE,
                    () -> data.clientPlayer.gameType(gameType));
            return;
        }
        if (event.getPacket() instanceof InventoryContentPacket packet
                && packet.getContainerId() == ContainerId.ARMOR) {
            var armor = List.copyOf(packet.getSlots());
            sendAcknowledgmentAfter(event, player, data, AcknowledgmentType.INVENTORY,
                    () -> data.clientPlayer.applyArmorContent(armor));
            return;
        }
        if (event.getPacket() instanceof InventorySlotPacket packet
                && packet.getContainerID() == ContainerId.ARMOR) {
            int slot = packet.getSlot();
            var item = packet.getItem();
            sendAcknowledgmentAfter(event, player, data, AcknowledgmentType.INVENTORY,
                    () -> data.clientPlayer.applyArmorSlot(slot, item));
            return;
        }
        if (event.getPacket() instanceof UpdateAbilitiesPacket packet
                && packet.getData() != null
                && packet.getData().getTargetPlayerRawId() == player.getId()) {
            boolean mayFly = false;
            boolean flying = false;
            boolean noClip = false;
            for (var layer : packet.getData().getLayers()) {
                if (layer.getAbilitiesSet().contains(AbilitiesIndex.MAY_FLY)) {
                    mayFly |= layer.getAbilityValues().contains(AbilitiesIndex.MAY_FLY);
                }
                if (layer.getAbilitiesSet().contains(AbilitiesIndex.FLYING)) {
                    flying |= layer.getAbilityValues().contains(AbilitiesIndex.FLYING);
                }
                if (layer.getAbilitiesSet().contains(AbilitiesIndex.NO_CLIP)) {
                    noClip |= layer.getAbilityValues().contains(AbilitiesIndex.NO_CLIP);
                }
            }
            boolean acknowledgedMayFly = mayFly;
            boolean acknowledgedFlying = flying;
            boolean acknowledgedNoClip = noClip;
            sendAcknowledgmentAfter(event, player, data, AcknowledgmentType.ABILITIES,
                    () -> data.motion.abilities(acknowledgedMayFly,
                            acknowledgedFlying, acknowledgedNoClip));
            return;
        }
        if (event.getPacket() instanceof UpdateAttributesPacket packet
                && packet.getRuntimeID() == player.getId()) {
            List<Runnable> updates = new ArrayList<>();
            for (var attribute : packet.getAttributeList()) {
                if ("minecraft:movement".equals(attribute.getAttributeName())) {
                    double movementSpeed = attribute.getCurrentValue();
                    double defaultMovementSpeed = attribute.getDefaultValue();
                    updates.add(() -> {
                        data.clientMovementSpeed = movementSpeed;
                        data.clientDefaultMovementSpeed = defaultMovementSpeed;
                        data.predictedMovementSpeed = movementSpeed;
                        data.motion.movementSpeed((float) movementSpeed);
                        data.motion.defaultMovementSpeed((float) defaultMovementSpeed);
                    });
                } else if ("minecraft:health".equals(attribute.getAttributeName())) {
                    boolean alive = attribute.getCurrentValue() > 0.0f;
                    updates.add(() -> data.motion.alive(alive));
                }
            }
            if (!updates.isEmpty()) {
                sendAcknowledgmentAfter(event, player, data, AcknowledgmentType.ATTRIBUTES,
                        () -> updates.forEach(Runnable::run));
            }
            return;
        }
        if (event.getPacket() instanceof MobEffectPacket packet
                && packet.getTargetRuntimeID() == player.getId()
                && (packet.getEffectID() == EffectType.JUMP_BOOST.id()
                || packet.getEffectID() == EffectType.LEVITATION.id()
                || packet.getEffectID() == EffectType.SLOW_FALLING.id())) {
            int effectId = packet.getEffectID();
            Integer amplifier = packet.getEvent() == MobEffectPacket.Event.REMOVE
                    ? null : packet.getEffectAmplifier();
            int duration = packet.getEffectDurationTicks();
            sendAcknowledgmentAfter(event, player, data, AcknowledgmentType.EFFECT, () -> {
                if (effectId == EffectType.JUMP_BOOST.id()) {
                    data.clientJumpBoostAmplifier = amplifier;
                    data.clientJumpBoostKnown = true;
                    data.motion.jumpBoost(amplifier == null ? 0 : amplifier + 1, duration);
                } else if (effectId == EffectType.LEVITATION.id()) {
                    data.clientLevitationAmplifier = amplifier;
                    data.clientLevitationKnown = true;
                    data.motion.levitation(amplifier == null ? 0 : amplifier + 1, duration);
                } else {
                    data.clientSlowFalling = amplifier != null;
                    data.clientSlowFallingKnown = true;
                    data.motion.slowFalling(amplifier != null, duration);
                }
            });
            return;
        }
        if (event.getPacket() instanceof UpdateBlockPacket packet) {
            queueBlockUpdate(player, data, packet.getBlockPosition(), packet.getLayer(),
                    packet.getDefinition() == null ? -1 : packet.getDefinition().getRuntimeId());
            flushBlockUpdatesAfter(event, player, data);
            return;
        }
        if (event.getPacket() instanceof UpdateSubChunkBlocksPacket packet) {
            for (var update : packet.getStandardBlocks()) {
                queueBlockUpdate(player, data, update.getPos(), 0, update.getDefinition().getRuntimeId());
            }
            for (var update : packet.getExtraBlocks()) {
                queueBlockUpdate(player, data, update.getPos(), 1, update.getDefinition().getRuntimeId());
            }
            flushBlockUpdatesAfter(event, player, data);
            return;
        }
        if (event.getPacket() instanceof AddPlayerPacket packet) {
            if (packet.getTargetRuntimeID() != player.getId()
                    && data.clientEntities.queueAdd(packet.getTargetRuntimeID(), packet.getPosition(),
                    true, packet.getActorData(), null)) {
                flushEntityTrackerAfter(event, player, data);
            }
            return;
        }
        if (event.getPacket() instanceof AddActorPacket packet) {
            if (packet.getTargetRuntimeID() != player.getId()
                    && data.clientEntities.queueAdd(packet.getTargetRuntimeID(), packet.getPosition(),
                    false, packet.getActorData(), packet.getActorType())) {
                flushEntityTrackerAfter(event, player, data);
            }
            return;
        }
        if (event.getPacket() instanceof MovePlayerPacket packet) {
            if (packet.getPlayerRuntimeID() == player.getId()) {
                if (packet.getPositionMode() != PositionMode.ONLY_HEAD_ROT) {
                    PlayerData.DirectSetback setback = data.consumeDirectSetback();
                    if (setback != null) {
                        packet.setPosition(setback.packetPosition());
                        packet.setOnGround(setback.onGround());
                    } else {
                        Vector3f serverPosition = packet.getPosition();
                        float feetY = serverPosition.getY() - player.getEyeHeight();
                        packet.setPosition(Vector3f.from(serverPosition.getX(),
                                feetY + MovementConstants.PLAYER_HEIGHT_OFFSET,
                                serverPosition.getZ()));
                    }
                    if (data.hasMovementCorrection()) {
                        sendAcknowledgmentAfter(event, player, data, AcknowledgmentType.MOVEMENT_CORRECTION,
                                () -> {
                                    data.finishMovementCorrection();
                                    data.motion.removePendingCorrection();
                                });
                    } else {
                        Vector3f correction = packet.getPosition();
                        boolean onGround = packet.isOnGround();
                        FloatVector feet = new FloatVector(correction.getX(),
                                correction.getY() - MovementConstants.PLAYER_HEIGHT_OFFSET,
                                correction.getZ());
                        data.motion.addPendingTeleport(feet);
                        data.addPendingTeleport();
                        sendAcknowledgmentAfter(event, player, data, AcknowledgmentType.TELEPORT,
                                () -> {
                                    data.motion.teleport(feet, onGround,
                                            packet.getPositionMode() == PositionMode.NORMAL);
                                    data.motion.removePendingTeleport();
                                    acceptServerCorrection(player, data, correction, null, onGround);
                                });
                    }
                }
            } else if (packet.getPositionMode() != PositionMode.ONLY_HEAD_ROT
                    && data.clientEntities.queueAbsolute(packet.getPlayerRuntimeID(), packet.getPosition(),
                    packet.getPositionMode() == PositionMode.TELEPORT)) {
                flushEntityTrackerAfter(event, player, data);
            }
            return;
        }
        if (event.getPacket() instanceof MoveActorAbsolutePacket packet) {
            var move = packet.getMoveData();
            if (move != null && move.getActorRuntimeID() != player.getId()
                    && data.clientEntities.queueAbsolute(move.getActorRuntimeID(), move.getPos(), move.isTeleported())) {
                flushEntityTrackerAfter(event, player, data);
            }
            return;
        }
        if (event.getPacket() instanceof MoveActorDeltaPacket packet) {
            var move = packet.getMoveData();
            if (move != null && move.getActorRuntimeID() != player.getId()
                    && data.clientEntities.queueDelta(move.getActorRuntimeID(), move.getNewPositionX(),
                    move.getNewPositionY(), move.getNewPositionZ(),
                    packet.getFlags().contains(MoveActorDeltaPacket.Flag.TELEPORTING))) {
                flushEntityTrackerAfter(event, player, data);
            }
            return;
        }
        if (event.getPacket() instanceof RemoveActorPacket packet) {
            if (data.clientEntities.queueRemove(packet.getTargetActorID())) {
                flushEntityTrackerAfter(event, player, data);
            }
            return;
        }
        if (event.getPacket() instanceof SetActorDataPacket packet) {
            if (packet.getTargetRuntimeID() == player.getId()) {
                data.lastActorData = packet.clone();
                var actorData = packet.getActorData();
                var flags = actorData.getFlags();
                Float width = actorData.get(ActorDataTypes.WIDTH);
                Float height = actorData.get(ActorDataTypes.HEIGHT);
                Float scale = actorData.get(ActorDataTypes.SCALE);
                if (flags != null || width != null || height != null || scale != null) {
                    boolean sprinting = flags != null && flags.contains(ActorFlags.SPRINTING);
                    sendAcknowledgmentAfter(event, player, data, AcknowledgmentType.ACTOR_DATA, () -> {
                        if (flags != null) {
                            data.clientServerSprinting = sprinting;
                            data.clientServerSprintApplied = false;
                            data.motion.serverSprint(sprinting);
                            data.motion.immobile(flags.contains(ActorFlags.NO_AI));
                            data.motion.affectedByGravity(
                                    flags.contains(ActorFlags.HAS_GRAVITY));
                            if (!flags.contains(ActorFlags.USING_ITEM)) {
                                data.motion.consuming(false);
                            }
                        }
                        if (width != null) data.clientWidth = width;
                        if (height != null) data.clientHeight = height;
                        if (scale != null) data.clientScale = scale;
                        data.motion.size(
                                width == null ? data.motion.width() : width,
                                height == null ? data.motion.height() : height,
                                scale == null ? data.motion.scale() : scale);
                    });
                }
            } else if (data.clientEntities.queueSize(packet.getTargetRuntimeID(), packet.getActorData())) {
                flushEntityTrackerAfter(event, player, data);
            }
            return;
        }
        if (event.getPacket() instanceof CorrectPlayerMovePredictionPacket packet
                && packet.getPredictionType() == PredictionType.PLAYER) {
            Vector3f correction = packet.getPos();
            Vector3f correctionVelocity = packet.getPosDelta();
            boolean onGround = packet.isOnGround();
            if (data.hasMovementCorrection()) {
                sendAcknowledgmentAfter(event, player, data, AcknowledgmentType.MOVEMENT_CORRECTION,
                        () -> {
                            data.finishMovementCorrection();
                            data.motion.removePendingCorrection();
                        });
            } else {
                FloatVector feet = new FloatVector(correction.getX(),
                        correction.getY() - MovementConstants.PLAYER_HEIGHT_OFFSET,
                        correction.getZ());
                data.motion.addPendingTeleport(feet);
                data.addPendingTeleport();
                sendAcknowledgmentAfter(event, player, data, AcknowledgmentType.TELEPORT,
                        () -> {
                            data.motion.teleport(feet, onGround, false);
                            if (correctionVelocity != null) {
                                data.motion.velocity(new FloatVector(correctionVelocity.getX(),
                                        correctionVelocity.getY(), correctionVelocity.getZ()));
                            }
                            data.motion.removePendingTeleport();
                            acceptServerCorrection(player, data, correction,
                                    correctionVelocity, onGround);
                        });
            }
            return;
        }
        if (event.getPacket() instanceof SetActorMotionPacket packet
                && packet.getTargetRuntimeID() == player.getId()) {
            Vector3f motion = packet.getMotion();
            Vec3 velocity = new Vec3(motion.getX(), motion.getY(), motion.getZ());
            long velocityEpoch = data.velocityEpoch();
            sendAcknowledgmentAfter(event, player, data, AcknowledgmentType.VELOCITY, () -> {
                if (!data.isVelocityEpoch(velocityEpoch)) return;
                data.acknowledgeVelocities(List.of(velocity));
                data.motion.knockback(new FloatVector((float) velocity.x(),
                        (float) velocity.y(), (float) velocity.z()));
            });
            return;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFall(EntityFallEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        PlayerData data = players.get(player.getUniqueId());
        if (data == null || data.pendingFallTick < 0) return;
        data.fallEventObserved = true;
        data.fallEventCancelled = event.isCancelled();
        data.observedServerFallDistance = event.getFallDistance();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)
                || event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        PlayerData data = players.get(player.getUniqueId());
        if (data == null || data.pendingFallTick < 0) return;
        data.fallDamageObserved = true;
        data.observedFallDamage = event.getFinalDamage();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWindChargeDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)
                || !(event.getDamager() instanceof EntityWindCharge charge)) return;
        registerWindCharge(player, charge);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWindChargeHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof EntityWindCharge charge)) return;
        for (Player player : plugin.getServer().getOnlinePlayers().values()) {
            if (player.getLevel() != charge.getLevel() || player.distanceSquared(charge) >= 4.0) continue;
            registerWindCharge(player, charge);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityMotion(EntityMotionEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        PlayerData data = players.get(player.getUniqueId());
        if (data == null || !data.joined || event.getMotion() == null) return;
        var motion = event.getMotion();
        if (!Double.isFinite(motion.x) || !Double.isFinite(motion.y) || !Double.isFinite(motion.z)) return;
        data.addServerMotionCandidate(new Vec3(motion.x, motion.y, motion.z));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpearStab(PlayerSpearStabEvent event) {
        Player player = event.getPlayer();
        PlayerData data = players.get(player.getUniqueId());
        if (data == null || !data.joined || !(event.getItem() instanceof ItemSpear spear)
                || !spear.canLunge(player)) return;
        int level = spear.getEnchantmentLevel(Enchantment.ID_LUNGE);
        var direction = player.getDirectionVector();
        direction.y = 0;
        if (direction.lengthSquared() == 0) return;
        direction = direction.normalize().multiply(0.5 + level * 0.4);
        var motion = player.getMotion();
        data.setSpearLungeCandidate(new Vec3(motion.x + direction.x, motion.y, motion.z + direction.z));
    }

    private void inspectMovement(PacketReceiveEvent event, Player player, PlayerAuthInputPacket packet) {
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
            fail(event, player, data, CheckType.BAD_PACKET_A, 4,
                    "non-finite movement", true);
            return;
        }
        if (Math.abs(rotation.getX()) > 90.01f
                || Math.abs(rotation.getY()) > 3600.0f
                || Math.abs(rotation.getZ()) > 3600.0f) {
            fail(event, player, data, CheckType.BAD_PACKET_B, 2,
                    "invalid rotation", true);
            return;
        }

        long tick = packet.getClientTick();
        if (data.lastTick >= 0 && tick <= data.lastTick) {
            boolean moved = data.lastPosition != null
                    && MovementCheckSupport.squared(clientPosition.getX(), clientPosition.getY(),
                    clientPosition.getZ(), data.lastPosition.getX(), data.lastPosition.getY(),
                    data.lastPosition.getZ()) > 1.0E-8;
            if (moved) {
                fail(event, player, data, CheckType.BAD_PACKET_B, 2,
                        "movement on stale tick=" + tick + " last=" + data.lastTick, true);
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
        inspectBlockActions(event, player, data, packet, clientPosition);
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
                accumulateSimulationOffset(event, player, data, result);
                if (result.correctionRequired() && !data.nearServerMotionTick) {
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
        var combatRotation = rotation;
        data.history.capture(player, tick, new Vec3(position.getX(), position.getY(), position.getZ()),
                data.predictedVelocity, combatRotation.getY(), combatRotation.getX(),
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

    private void inspectCombat(PacketReceiveEvent event, Player player, InventoryTransactionPacket packet) {
        if (!(packet.getTransaction() instanceof ItemUseOnActorInventoryTransaction transaction)
                || transaction.getActionType() != ItemUseOnActorActionType.ATTACK) return;
        PlayerData data = players.get(player.getUniqueId());
        if (data == null) {
            return;
        }
        Entity target = player.getLevel().getEntity(transaction.getRuntimeId());
        if (target == null) {
            event.setCancelled();
            return;
        }
        if (target == player) {
            fail(event, player, data, CheckType.KILL_AURA_A, 1, "invalid target", true);
            return;
        }
        if (!(target instanceof Player)) return;
        if (data.inGrace()) return;
        var clientEntity = data.clientEntities.view(target.getId());
        if (clientEntity != null && clientEntity.player() && clientEntity.ticksSinceTeleport() <= 10) return;
        var settings = plugin.settings();
        double expansion = Math.max(0, settings.combatBboxExpansion());
        double leniency = Math.max(0, settings.combatReachLeniency());
        int steps = Math.max(1, settings.combatInterpolationSteps());
        double maximumAngle = settings.combatMaximumAttackAngle();
        double closeRange = Math.max(0, settings.combatCloseRangeFallback());
        double closeAngle = Math.max(0, settings.combatCloseRangeAngle());
        CombatPredictionResult result = combatPredictor.predict(player, data, target,
                expansion, leniency, steps, maximumAngle, closeRange, closeAngle, data.touchInput);
        if (result.rawDistance() > 2.9) {
            fail(event, player, data, CheckType.REACH_A, 1,
                    "raw=" + NetworkCheckSupport.format(result.rawDistance()) + " ray=" + NetworkCheckSupport.format(result.rayDistance()), true);
        } else if (!result.raycastHit() || result.angle() > maximumAngle) {
            fail(event, player, data, CheckType.HITBOX_A, 1,
                    "angle=" + NetworkCheckSupport.format(result.angle()) + " raw=" + NetworkCheckSupport.format(result.rawDistance()), true);
        } else if (!result.valid()) {
            fail(event, player, data, CheckType.REACH_A, 1,
                    "ray=" + NetworkCheckSupport.format(result.rayDistance()) + " raw=" + NetworkCheckSupport.format(result.rawDistance()), true);
        }
    }

    private void trackGlideBoost(Player player, PlayerData data, InventoryTransactionPacket packet) {
        if (!data.motion.gliding()
                || !(packet.getTransaction() instanceof ItemUseInventoryTransaction transaction)
                || transaction.getActionType() != ItemUseActionType.USE) return;
        var item = player.getInventory().getItemInMainHand();
        if (item != null && ItemID.FIREWORK_ROCKET.equals(item.getId())) {
            data.motion.glideBoostTicks(MovementConstants.GLIDE_BOOST_TICKS);
        }
    }

    private void trackItemUseState(Player player, PlayerData data,
                                   InventoryTransactionPacket packet) {
        if (packet.getTransaction() instanceof ItemReleaseInventoryTransaction) {
            data.motion.consuming(false);
            return;
        }
        if (!(packet.getTransaction() instanceof ItemUseInventoryTransaction transaction)
                || transaction.getActionType() != ItemUseActionType.USE) {
            return;
        }
        Item item = player.getInventory().getItemInMainHand();
        if (item != null && !item.isNull() && item.isConsumable()
                && item.getUseDuration() > 0.0f) {
            data.motion.consuming(true);
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
            fail(event, player, data, CheckType.BAD_PACKET_C, 2, "vehicle-id=" + predictedId, true);
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
        double tolerance = settings.vehicleTolerance();
        if (result.offset() > tolerance) {
            data.vehicleBuffer += Math.min(result.offset() - tolerance, 0.75);
            double threshold = settings.vehicleBufferThreshold();
            if (data.vehicleBuffer > threshold) {
                fail(event, player, data, CheckType.VEHICLE_A, 1,
                        "type=" + result.type() + " offset=" + NetworkCheckSupport.format(result.offset()),
                        true, false);
                scheduleVehicleCorrection(player, data, vehicle);
                data.vehicleBuffer = threshold * 0.5;
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

    private void inspectBlockActions(PacketReceiveEvent event, Player player, PlayerData data,
                                     PlayerAuthInputPacket packet, Vector3f position) {
        if (!packet.getInputData().contains(PlayerAuthInputData.PERFORM_BLOCK_ACTIONS)) return;
        List<PlayerBlockActionData> actions = packet.getPlayerBlockActions();
        if (actions == null || actions.isEmpty()) return;
        int maximum = plugin.settings().maxPacketActions();
        if (actions.size() > maximum) {
            fail(event, player, data, CheckType.BAD_PACKET_C, 2, "block-actions=" + actions.size(), true);
            return;
        }
        // a setback moves the player without the client knowing yet, so the block it is still mining
        // is measured from a position it never had; the distance is the plugin's doing, not a cheat
        boolean correcting = data.hasMovementCorrection() || data.hasPendingTeleport()
                || data.setbackTeleportPending || data.inGrace();
        long now = System.nanoTime();
        boolean groundedForBreak = breakGrounded(player, data, position);
        for (PlayerBlockActionData action : actions) {
            PlayerActionType type = action.getPlayerActionType();
            if (!isBreakAction(type)) continue;
            Vector3i pos = action.getBlockPosition();
            if (pos == null) continue;
            double reach = Math.sqrt(MovementCheckSupport.squared(position.getX(), position.getY(), position.getZ(),
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
            double maximumReach = Math.min(7.0,
                    Math.max(1.0, plugin.settings().blocksMaxReach()));
            if (reach > maximumReach && !correcting) {
                fail(event, player, data, CheckType.BREAK_REACH, 1, "distance=" + NetworkCheckSupport.format(reach), true);
                return;
            }

            if (type == PlayerActionType.ABORT_DESTROY_BLOCK
                    || type == PlayerActionType.STOP_DESTROY_BLOCK) {
                data.resetBreak();
                continue;
            }
            if (type == PlayerActionType.START_DESTROY_BLOCK || type == PlayerActionType.CRACK_BLOCK) {
                if (!sameBlock(data.breakingBlock, pos)) {
                    startBreaking(player, data, pos, now, groundedForBreak);
                }
                advanceBreakProgress(data, packet.getClientTick());
                continue;
            }
            if (type == PlayerActionType.CONTINUE_DESTROY_BLOCK) {
                if (!sameBlock(data.breakingBlock, pos)) {
                    startBreaking(player, data, pos, now, groundedForBreak);
                }
                advanceBreakProgress(data, packet.getClientTick());
                continue;
            }
            if (type == PlayerActionType.PREDICT_DESTROY_BLOCK) {
                Block block = clientBreakBlock(player, data, pos);
                long required = Math.max(data.requiredBreakNanos,
                        requiredBreakNanos(player, block, groundedForBreak));
                long leniencyMs = fastBreakLeniencyMs(data, packet.getClientTick());
                long leniency = leniencyMs * 1_000_000L;
                advanceBreakProgress(data, packet.getClientTick());
                boolean validState = sameBlock(data.breakingBlock, pos)
                        && block.getId().equals(data.breakingBlockId);
                if (!validState) {
                    event.setCancelled();
                    data.resetBreak();
                    return;
                }
                boolean progressComplete = data.breakProgress >= 1.0;
                if (!progressComplete && now - data.breakStartedNanos + leniency < required) {
                    // Do not judge the first break frames before a latency probe has
                    // completed. PNX can deliver START/PREDICT in the same tick.
                    if (data.network.stackLatencyMillis() <= 0) {
                        data.resetBreak();
                        continue;
                    }
                    event.setCancelled();
                    if (registerEarlyBreak(data, now)) {
                        fail(event, player, data, CheckType.FAST_BREAK_A, 1,
                                "elapsed=" + Math.max(0, (now - data.breakStartedNanos) / 1_000_000L)
                                        + "ms required=" + required / 1_000_000L + "ms leniency=" + leniencyMs
                                        + "ms progress=" + NetworkCheckSupport.format(data.breakProgress)
                                        + " rtt=" + data.network.stackLatencyMillis() + "ms block=" + block.getId(), true);
                    }
                    data.resetBreak();
                    return;
                }
                data.earlyBreakAttempts = 0;
                data.earlyBreakWindowNanos = 0;
                data.resetBreak();
            }
        }
    }

    private static boolean isBreakAction(PlayerActionType type) {
        return type == PlayerActionType.START_DESTROY_BLOCK
                || type == PlayerActionType.CRACK_BLOCK
                || type == PlayerActionType.CONTINUE_DESTROY_BLOCK
                || type == PlayerActionType.PREDICT_DESTROY_BLOCK
                || type == PlayerActionType.ABORT_DESTROY_BLOCK
                || type == PlayerActionType.STOP_DESTROY_BLOCK;
    }

    private static boolean sameBlock(Vector3i first, Vector3i second) {
        return first != null && second != null && first.getX() == second.getX()
                && first.getY() == second.getY() && first.getZ() == second.getZ();
    }

    private static void startBreaking(Player player, PlayerData data, Vector3i position,
                                      long now, boolean grounded) {
        Block block = clientBreakBlock(player, data, position);
        data.breakingBlock = position;
        data.breakingBlockId = block.getId();
        data.breakStartedNanos = now;
        data.requiredBreakNanos = requiredBreakNanos(player, block, grounded);
        data.breakProgress = 0;
        data.breakProgressTick = Long.MIN_VALUE;
    }

    private static void advanceBreakProgress(PlayerData data, long clientTick) {
        if (data.breakProgressTick == clientTick || data.requiredBreakNanos <= 0
                || data.requiredBreakNanos == Long.MAX_VALUE) return;
        double requiredTicks = Math.max(1.0, data.requiredBreakNanos / 50_000_000.0);
        data.breakProgress = Math.min(1.0, data.breakProgress + 1.0 / requiredTicks);
        data.breakProgressTick = clientTick;
    }

    private static boolean registerEarlyBreak(PlayerData data, long now) {
        if (data.earlyBreakWindowNanos == 0
                || now - data.earlyBreakWindowNanos > 5_000_000_000L) {
            data.earlyBreakWindowNanos = now;
            data.earlyBreakAttempts = 0;
        }
        data.earlyBreakAttempts++;
        // Bedrock can emit START and PREDICT in separate frames when the client
        // is catching up. Require a longer repeated pattern before alerting.
        if (data.earlyBreakAttempts < 8) return false;
        data.earlyBreakAttempts = 0;
        data.earlyBreakWindowNanos = now;
        return true;
    }

    private static Block clientBreakBlock(Player player, PlayerData data, Vector3i position) {
        Block server = player.getLevel().getBlock(position.getX(), position.getY(), position.getZ());
        BlockFrame client = data.clientWorld.resolve(position.getX(), position.getY(), position.getZ(), 0,
                BlockFrame.capture(server));
        if (client == null || client.id().equals(server.getId())) return server;
        return Block.get(client.id(), player.getLevel(), position.getX(), position.getY(), position.getZ());
    }

    private static long requiredBreakNanos(Player player, Block block, boolean grounded) {
        if (player.isCreative()) return 0;
        var held = player.getInventory().getItemInMainHand();
        double seconds = grounded
                ? block.calculateBreakTimeNotInAir(held, player)
                : block.calculateBreakTime(held, player);
        if (!Double.isFinite(seconds) || seconds < 0) return Long.MAX_VALUE;
        return (long) Math.min(Long.MAX_VALUE, Math.ceil(seconds * 1_000_000_000.0));
    }

    private static boolean breakGrounded(Player player, PlayerData data, Vector3f position) {
        if (player.isOnGround() || data.predictedOnGround) return true;
        return data.lastPosition != null
                && Math.abs(position.getY() - data.lastPosition.getY()) <= 0.08
                && MovementCheckSupport.serverGround(player, position);
    }

    private long fastBreakLeniencyMs(PlayerData data, long clientTick) {
        var settings = plugin.settings();
        long configured = Math.max(0, settings.blocksBreakLeniencyMs());
        long roundTrip = Math.max(0, data.network.stackLatencyMillis());
        long oneWay = Math.min(500, (roundTrip + 1) / 2);
        long tickCatchup = 0;
        if (data.lastTick >= 0 && clientTick > data.lastTick) {
            tickCatchup = Math.min(250, (clientTick - data.lastTick) * 50);
        }
        return Math.min(750, configured + oneWay + tickCatchup);
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
        fail(event, player, data, CheckType.NO_FALL_A, 1, detail, true);
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

    private void fail(PacketReceiveEvent event, Player player, PlayerData data, CheckType check,
                      double amount, String detail, boolean cancel) {
        fail(event, player, data, check, amount, detail, cancel, true);
    }

    /**
     * A wind charge and a spear lunge are both bursts the client predicts on its own, so the server
     * never sends a motion packet the simulation could follow. Both already have their candidate
     * impulses computed; whichever one matches what the player actually did is handed to the
     * simulator as knockback, so the burst becomes a predicted move instead of an unexplained one.
     */
    private void applyBurstCandidates(PlayerData data, Vec3 observedMovement) {
        // a burst and a hit can land on the same tick, and the sum resembles neither on its own, so
        // the impulse already armed for this tick is offered combined with each candidate too
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

    /**
     * The 0.5 block correction threshold only exists to decide when to rubber-band the player, and a
     * cheat that stays just under it, or that only offends every other tick, never reaches it. This
     * accumulates the offset the simulation could not explain so a small but persistent divergence —
     * which is exactly what flight and speed look like — eventually flags on its own.
     */
    private void accumulateSimulationOffset(PacketReceiveEvent event, Player player,
                                            PlayerData data, MovementPipelineResult result) {
        // an offset measured without re-anchoring is drift carried over from earlier ticks, and
        // counting it feeds a loop: violation, setback, no re-anchor, same offset again
        if (!result.anchored() || data.inGrace() || data.hasMovementCorrection()
                || data.hasPendingTeleport()) {
            return;
        }
        if (data.movementPacketDropped) {
            data.movementPacketDropped = false;
            return;
        }

        // No movement covers this in one tick, so it is a desync — a teleport that never reached us,
        // a world change, a resend. The simulation re-anchors on its own; counting it as a physics
        // failure only reports how far apart the two were.
        if (result.offset() > DESYNC_OFFSET) {
            data.simulationOffsetBuffer = 0.0;
            return;
        }

        double tolerance = plugin.settings().predictionTolerance();
        if (data.nearServerMotionTick) {
            tolerance *= 4.0;
        }

        double excess = Math.max(0.0, result.offset() - tolerance);
        data.simulationOffsetBuffer = Math.max(0.0, data.simulationOffsetBuffer + excess
                - plugin.settings().predictionBufferDecay());

        if (data.simulationOffsetBuffer <= 0.0 && result.onGround()) {
            // only ground counts: a spot reached in mid-air may already be part of the flight being
            // checked, and sending the player back to it would confirm the cheat instead of undoing it
            data.lastVerifiedPosition = new Vec3(result.clientPosition().x(),
                    result.clientPosition().y() + MovementConstants.CORRECTION_HEIGHT_OFFSET,
                    result.clientPosition().z());
        }

        double threshold = Math.max(0.05, plugin.settings().predictionBufferThreshold());
        if (data.simulationOffsetBuffer < threshold) {
            return;
        }

        data.simulationOffsetBuffer = 0.0;
        fail(event, player, data, CheckType.SIMULATION, 1,
                "offset=" + NetworkCheckSupport.format((float) result.offset())
                        + " client=" + result.clientPosition()
                        + " predicted=" + result.authoritativePosition()
                        + " ground=" + result.onGround()
                        + " support=" + result.supportingBlock() + "@" + result.supportingBlockY()
                        + " vy=" + NetworkCheckSupport.format((float) result.authoritativeVelocity().y())
                        + (result.inFluid() ? " fluid" : "")
                        + (result.impulseApplied() ? " kb-applied" : "")
                        + (result.impulseDeferred() ? " kb-deferred" : "")
                        + (data.nearServerMotionTick ? " impulse" : "")
                        + " tick=" + result.tick(), false, false);

        // the buffer is the signal that survives a cheat pacing itself, so it decides the setback too
        double violations = data.violations.getOrDefault(CheckType.SIMULATION.id(), 0.0);
        if (violations >= Math.max(1.0, plugin.settings().setbackViolations())) {
            scheduleMovementCorrection(player, data);
        }
    }

    public void handleEarlyMovementRejection(Player player, MovementPreValidationResult result) {
        if (result.check() == null || !player.isOnline()
                || player.hasPermission("amethyst.bypass")) return;
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

    private void fail(PacketReceiveEvent event, Player player, PlayerData data, CheckType check,
                      double amount, String detail, boolean cancel, boolean setback) {
        double vl = data.violations.merge(check.id(), amount, Double::sum);
        long now = System.nanoTime();
        if (now - data.lastAlertNanos > 300_000_000L) {
            plugin.alert(player, check, vl, detail);
            data.lastAlertNanos = now;
        }
        if (cancel) event.setCancelled();
        double setbackThreshold = Math.max(1.0, plugin.settings().setbackViolations());
        if (setback && cancel && MovementCheckSupport.isMovementCheck(check) && vl >= setbackThreshold) {
            scheduleMovementCorrection(player, data);
        }
    }

    private void scheduleMovementCorrection(Player player, PlayerData data) {
        Vec3 position = data.lastVerifiedPosition;
        if (position == null) return;
        if (!data.beginMovementCorrection()) return;
        directTeleportSetback(player, data, position.x(),
                position.y() - MovementConstants.CORRECTION_HEIGHT_OFFSET, position.z(),
                data.predictedOnGround,
                "position=" + position + " tick=" + Math.max(0, data.lastTick));
    }

    /**
     * Sends the vehicle back rather than the rider. Correcting the player alone would pull them out of
     * a boat that stays wherever the cheat left it, and the next tick puts them straight back in it.
     */
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

            // The MovePlayerPacket produced by Player#teleport must use the normal
            // teleport ACK path instead of the old prediction-correction path.
            data.finishMovementCorrection();
            if (!player.teleport(target, PlayerTeleportEvent.TeleportCause.PLUGIN)) {
                data.setbackTeleportPending = false;
                data.clearDirectSetback();
                data.finishSimulationCorrectionEpisode();
                return;
            }
            data.safeLocation = player.getLocation();
            if (data.beginSimulationCorrectionEpisode()) {
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

    private void kick(Player player, PlayerData data) {
        if (data.kickScheduled) {
            return;
        }
        data.kickScheduled = true;
        plugin.getServer().getScheduler().scheduleTask(plugin, () -> {
            if (player.isOnline()) player.kick("Invalid packet");
        });
    }

    private PlayerData data(Player player) {
        return players.computeIfAbsent(player.getUniqueId(), ignored -> {
            PlayerData created = new PlayerData();
            created.safeLocation = player.getLocation();
            created.grantGrace(GRACE_MILLIS);
            return created;
        });
    }

    private void acceptServerCorrection(Player player, PlayerData data, Vector3f position,
                                        Vector3f velocity, boolean onGround) {
        data.acknowledgePendingTeleport();
        if (position == null || !MovementCheckSupport.finite(position)) {
            data.finishMovementCorrection();
            return;
        }
        synchronizeCorrection(data, position);
        data.predictedVelocity = MovementCheckSupport.finite(velocity)
                ? new Vec3(velocity.getX(), velocity.getY(), velocity.getZ())
                : Vec3.ZERO;
        data.predictedOnGround = onGround;
        data.predictedHorizontalCollision = false;
        data.penetratedLastFrame = false;
        data.stuckInCollider = false;
        data.simulationMismatchFrames = 0;
        data.finishSimulationCorrectionEpisode();
        data.finishMovementCorrection();
    }

    private static void synchronizeCorrection(PlayerData data, Vector3f position) {
        data.lastPosition = position;
        data.authoritativePosition = new Vec3(
                position.getX(), position.getY(), position.getZ());
        data.jumpDelayTicks = 0;
        data.vehicleBuffer = 0;
    }

    private void flushEntityTrackerAfter(PacketSendEvent event, Player player, PlayerData data) {
        List<ClientEntityTracker.Update> updates = data.clientEntities.drainQueuedUpdates();
        if (updates.isEmpty() || !player.isOnline()) return;
        sendAcknowledgmentAfter(event, player, data, AcknowledgmentType.ENTITY,
                () -> data.clientEntities.acknowledge(updates));
    }

    private void queueBlockUpdate(Player player, PlayerData data, Vector3i position, int layer, int runtimeId) {
        if (position == null || runtimeId < 0) return;
        var state = Registries.BLOCKSTATE.get(runtimeId);
        if (state == null) return;
        Block block = Block.get(state, player.getLevel(), position.getX(), position.getY(), position.getZ(), layer);
        BlockFrame visibleState = null;
        var visibleFrame = data.history.latest();
        if (visibleFrame != null) {
            visibleState = visibleFrame.blockAt(position.getX(), position.getY(), position.getZ());
        }
        data.clientWorld.queue(position.getX(), position.getY(), position.getZ(), layer,
                BlockFrame.capture(block), visibleState);
    }

    private void flushBlockUpdatesAfter(PacketSendEvent event, Player player, PlayerData data) {
        Map<ClientWorldTracker.Key, ClientWorldTracker.Update> updates =
                data.clientWorld.drainQueuedUpdates();
        if (updates.isEmpty() || !player.isOnline()) return;
        sendAcknowledgmentAfter(event, player, data, AcknowledgmentType.WORLD,
                () -> data.clientWorld.acknowledge(updates));
    }

    private void sendAcknowledgment(Player player, PlayerData data, Runnable callback) {
        sendAcknowledgment(player, data, AcknowledgmentType.GENERIC, callback);
    }

    private void sendAcknowledgment(Player player, PlayerData data, AcknowledgmentType type, Runnable callback) {
        if (!data.network.addAcknowledgment(type, callback)) return;
        if (!player.isOnline()) {
            data.network.discardAcknowledgmentBatch();
            return;
        }
        NetworkStackLatencyPacket packet = acknowledgmentPacket(data);
        player.sendPacket(packet);
    }

    private void sendAcknowledgmentAfter(PacketSendEvent event, Player player, PlayerData data,
                                         AcknowledgmentType type, Runnable callback) {
        if (!data.network.addAcknowledgment(type, callback)) return;
        if (!player.isOnline()) {
            data.network.discardAcknowledgmentBatch();
            return;
        }
        NetworkStackLatencyPacket acknowledgment = acknowledgmentPacket(data);
        event.setCancelled();
        player.getSession().sendPacket(event.getPacket());
        player.getSession().sendPacket(acknowledgment);
    }

    private static NetworkStackLatencyPacket acknowledgmentPacket(PlayerData data) {
        NetworkStackLatencyPacket packet = new NetworkStackLatencyPacket();
        packet.setCreationTime(data.network.flushAcknowledgments(System.nanoTime()));
        packet.setFromServer(true);
        return packet;
    }

    private void runAcknowledgments(List<Runnable> callbacks) {
        if (callbacks.isEmpty()) return;
        if (plugin.getServer().isPrimaryThread()) {
            callbacks.forEach(Runnable::run);
        } else {
            plugin.getServer().getScheduler().scheduleTask(plugin, () -> callbacks.forEach(Runnable::run));
        }
    }

    private void registerWindCharge(Player player, EntityWindCharge charge) {
        PlayerData data = players.get(player.getUniqueId());
        if (data == null || !data.joined) return;
        Vec3 predicted = data.predictedVelocity == null ? Vec3.ZERO : data.predictedVelocity;
        double pushX = (player.x - charge.x) * 0.20;
        double pushZ = (player.z - charge.z) * 0.20;
        Vec3 pnx = new Vec3(predicted.x() * 0.5 + pushX,
                predicted.y() * 0.5 + 0.60, predicted.z() * 0.5 + pushZ);
        Vec3 bedrockRadial = predicted.add(pushX, 1.05, pushZ);
        Vec3 bedrockVertical = predicted.add(0, 1.05, 0);
        data.setWindChargeCandidates(List.of(pnx, bedrockRadial, bedrockVertical));
    }

}
