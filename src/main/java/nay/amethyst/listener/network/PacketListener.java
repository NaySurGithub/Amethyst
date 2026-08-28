package nay.amethyst.listener.network;

import nay.amethyst.AmethystPlugin;
import nay.amethyst.check.client.BedrockToolDetector;
import nay.amethyst.check.combat.MacroCheck;
import nay.amethyst.check.inventory.AutoTotemCheck;
import nay.amethyst.check.inventory.ChestStealerCheck;
import nay.amethyst.check.inventory.InventoryMoveCheck;
import nay.amethyst.check.packet.BadPacketCheck;
import nay.amethyst.check.type.CheckType;
import nay.amethyst.data.player.PlayerData;
import nay.amethyst.data.player.GraceReason;
import nay.amethyst.prediction.common.Vec3;
import nay.amethyst.tracking.entity.ClientEntityTracker;
import nay.amethyst.tracking.network.ack.AcknowledgmentType;
import nay.amethyst.history.model.BlockFrame;
import nay.amethyst.tracking.world.ClientWorldTracker;
import nay.amethyst.listener.network.support.MovementCheckSupport;
import nay.amethyst.listener.network.support.NetworkCheckSupport;
import nay.amethyst.listener.network.processor.BlockPacketProcessor;
import nay.amethyst.listener.network.processor.CombatPacketProcessor;
import nay.amethyst.listener.network.processor.MovementPacketProcessor;
import nay.amethyst.network.session.MovementSessionRegistry;
import nay.amethyst.packet.movement.MovementPreValidationResult;
import nay.amethyst.simulation.movement.FloatVector;
import nay.amethyst.simulation.movement.MovementConstants;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.actor.ActorEvent;
import org.cloudburstmc.protocol.bedrock.data.PlayerActionType;
import org.cloudburstmc.protocol.bedrock.packet.PlayerActionPacket;
import org.cloudburstmc.protocol.bedrock.packet.ActorEventPacket;
import org.cloudburstmc.protocol.bedrock.packet.AnimatePacket;
import org.cloudburstmc.protocol.bedrock.data.payload.inventory.transaction.ItemUseTriggerType;
import org.cloudburstmc.protocol.bedrock.packet.InteractPacket;
import org.cloudburstmc.protocol.bedrock.data.AbilitiesIndex;
import org.cloudburstmc.protocol.bedrock.data.actor.ActorDataTypes;
import org.cloudburstmc.protocol.bedrock.data.actor.ActorFlags;
import org.cloudburstmc.protocol.bedrock.data.BuildPlatform;
import org.cloudburstmc.protocol.bedrock.data.InputMode;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerId;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.data.PredictionType;
import org.cloudburstmc.protocol.bedrock.data.payload.move.PositionMode;
import org.cloudburstmc.protocol.bedrock.data.payload.inventory.transaction.ItemUseActionType;
import org.cloudburstmc.protocol.bedrock.data.payload.inventory.transaction.ItemUseOnActorActionType;
import org.cloudburstmc.protocol.bedrock.data.payload.inventory.transaction.data.ItemUseInventoryTransaction;
import org.cloudburstmc.protocol.bedrock.data.payload.inventory.transaction.ItemReleaseActionType;
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
import org.powernukkitx.event.block.BlockBreakEvent;
import org.powernukkitx.event.block.BlockPlaceEvent;
import org.powernukkitx.entity.Entity;
import org.powernukkitx.entity.EntityLiving;
import org.powernukkitx.entity.projectile.EntityProjectile;
import org.powernukkitx.entity.projectile.EntityWindCharge;
import org.powernukkitx.entity.effect.EffectType;
import org.powernukkitx.event.Cancellable;
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
import org.powernukkitx.event.player.PlayerBedEnterEvent;
import org.powernukkitx.event.player.PlayerBedLeaveEvent;
import org.powernukkitx.event.server.PacketReceiveEvent;
import org.powernukkitx.event.server.PacketSendEvent;
import org.powernukkitx.level.Location;
import org.powernukkitx.math.Vector3;
import org.powernukkitx.item.ItemID;
import org.powernukkitx.item.Item;
import org.powernukkitx.item.ItemSpear;
import org.powernukkitx.item.enchantment.Enchantment;
import org.powernukkitx.registry.Registries;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PacketListener implements Listener {
    private static final long GRACE_MILLIS = 2000;
    private static final double BAD_PACKET_D_KICK_VIOLATIONS = 35.0;
    private static final long TELEPORT_GRACE_MILLIS = 3000;
    private static final int HOTBAR_SIZE = 9;
    private static final Set<String> THROWN_FROM_HOTBAR = Set.of(ItemID.POTION,
            ItemID.SPLASH_POTION, ItemID.LINGERING_POTION, ItemID.ENDER_PEARL);
    private static final double TIMER_KICK_VIOLATIONS = 15.0;
    private static final int TIMER_WINDOW_TICKS = 60;
    private static final double TIMER_MAXIMUM_RATIO = 1.7;
    private static final double TIMER_PING_ALLOWANCE = 0.5;
    private static final int FAST_USE_MINIMUM_TICKS = 5;

    private final AmethystPlugin plugin;
    private final Map<UUID, PlayerData> players;
    private final MovementSessionRegistry movementSessions;
    private final InventoryMoveCheck inventoryMoveCheck = new InventoryMoveCheck();
    private final ChestStealerCheck chestStealerCheck = new ChestStealerCheck();
    private final AutoTotemCheck autoTotemCheck = new AutoTotemCheck();
    private final MacroCheck macroCheck = new MacroCheck();
    private final BadPacketCheck badPacketCheck = new BadPacketCheck();
    private final BlockPacketProcessor blockProcessor;
    private final CombatPacketProcessor combatProcessor;
    private final MovementPacketProcessor movementProcessor;

    public PacketListener(AmethystPlugin plugin, Map<UUID, PlayerData> players,
                          MovementSessionRegistry movementSessions) {
        this.plugin = plugin;
        this.players = players;
        this.movementSessions = movementSessions;
        this.blockProcessor = new BlockPacketProcessor(plugin, players, this::fail);
        this.combatProcessor = new CombatPacketProcessor(plugin, players, this::fail);
        this.movementProcessor = new MovementPacketProcessor(plugin, players, this::fail);
    }

    public void onServerTick() {
        if (players.isEmpty()) return;
        long now = System.nanoTime();
        for (Player player : plugin.getServer().getOnlinePlayers().values()) {
            PlayerData data = players.get(player.getUniqueId());
            if (data == null || !data.joined) continue;
            data.network.tick(now);
            inspectTimer(player, data);
            inspectMacro(player, data, now);
            if (data.network.shouldProbe()) sendAcknowledgment(player, data, null);
        }
    }

    private void inspectTimer(Player player, PlayerData data) {
        data.network.drainOverBudgetInputs();
        data.timerInputs += data.network.drainInputCount();
        data.timerTicks++;
        if (data.inGrace() || player.hasPermission("amethyst.bypass")) {
            data.timerInputs = 0;
            data.timerTicks = 0;
            data.timerWarmup = 0;
            return;
        }
        if (data.timerWarmup < Math.max(0, plugin.settings().timerWarmupPackets())) {
            data.timerWarmup++;
            data.timerInputs = 0;
            data.timerTicks = 0;
            return;
        }
        if (data.timerTicks < TIMER_WINDOW_TICKS) {
            return;
        }

        double ratio = data.timerInputs / (double) data.timerTicks;
        data.timerInputs = 0;
        data.timerTicks = 0;

        long ping = Math.max(0, NetworkCheckSupport.ping(player));
        double allowed = TIMER_MAXIMUM_RATIO + Math.min(TIMER_PING_ALLOWANCE, ping / 1000.0);
        if (ratio <= allowed) {
            data.timerBuffer = Math.max(0.0, data.timerBuffer - 1.0);
            return;
        }

        data.timerBuffer++;
        double threshold = Math.max(1.0, plugin.settings().timerViolationSamples() / 8.0);
        if (data.timerBuffer < threshold) {
            return;
        }

        data.timerBuffer = 0.0;
        int excess = (int) Math.round((ratio - 1.0) * 100);
        double vl = data.violations.merge(CheckType.TIMER.id(), 1.0, Double::sum);
        long now = System.nanoTime();
        if (now - data.lastAlertNanos > 300_000_000L) {
            plugin.alert(player, CheckType.TIMER, vl,
                    "ratio=" + NetworkCheckSupport.format(ratio) + " excess=" + excess + "%");
            data.lastAlertNanos = now;
        }
        if (vl >= TIMER_KICK_VIOLATIONS) {
            kick(player, data, "Amethyst timed out.");
            return;
        }
        if (vl >= Math.max(1.0, plugin.settings().setbackViolations())) {
            movementProcessor.scheduleMovementCorrection(player, data, false);
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
        data.grantGrace(GraceReason.CHUNK_LOADING, GRACE_MILLIS);
        movementSessions.reset(player);

        String tool = BedrockToolDetector.detect(player);
        if (tool != null) {
            double vl = data.violations.merge(CheckType.BEDROCK_TOOL_A.id(), 1.0, Double::sum);
            plugin.alert(player, CheckType.BEDROCK_TOOL_A, vl, tool);
            kick(player, data, "Unsupported client");
        }
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
        data.grantGrace(GraceReason.WORLD_CHANGE, GRACE_MILLIS);
        movementSessions.reset(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBedEnter(PlayerBedEnterEvent event) {
        PlayerData data = grantBedGrace(event.getPlayer());
        if (data != null) {
            data.inBed = true;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBedLeave(PlayerBedLeaveEvent event) {
        PlayerData data = grantBedGrace(event.getPlayer());
        if (data != null) {
            data.inBed = false;
            data.lastSleepingTick = data.lastTick;
        }
    }

    private PlayerData grantBedGrace(Player player) {
        if (player == null) {
            return null;
        }
        PlayerData data = players.get(player.getUniqueId());
        if (data == null || !data.joined) {
            return null;
        }
        data.lastPosition = null;
        data.grantGrace(GraceReason.TELEPORT, TELEPORT_GRACE_MILLIS);
        return data;
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
        data.safeLocation = event.getTo();
        data.lastPosition = null;
        GraceReason reason = event.getFrom().getLevel() == event.getTo().getLevel()
                ? GraceReason.TELEPORT : GraceReason.WORLD_CHANGE;
        data.grantGrace(reason, TELEPORT_GRACE_MILLIS);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        blockProcessor.handlePlace(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlaceReach(BlockPlaceEvent event) {
        blockProcessor.inspectPlaceReach(event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        blockProcessor.handleBreak(event);
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
            if (packet.getClientTick() > playerData.lastTick) {
                playerData.clientEntities.tick();
                playerData.tickClicks();
            }
            inspectInventoryMove(event, player, playerData, packet);
            movementProcessor.inspectMovement(event, player, packet, blockProcessor);
        } else if (event.getPacket() instanceof ItemStackRequestPacket packet) {
            long now = System.nanoTime();
            inventoryMoveCheck.handleRequest(playerData, packet, now);
            inspectChestStealer(event, player, playerData, packet, now);
            inspectAutoTotem(event, player, playerData, packet, now);
        } else if (event.getPacket() instanceof AnimatePacket packet) {
            if (packet.getAction() == AnimatePacket.Action.SWING) {
                playerData.clickLeft();
                long now = System.nanoTime();
                boolean inCombat = now - playerData.lastCombatNanos < 3_000_000_000L;
                playerData.recordLeftClick(now, inCombat);
            }
        } else if (event.getPacket() instanceof PlayerActionPacket packet) {
            inspectPlayerAction(event, player, playerData, packet);
        } else if (event.getPacket() instanceof InteractPacket packet) {
            inspectInteract(event, player, playerData, packet);
        } else if (event.getPacket() instanceof InventoryTransactionPacket packet) {
            if (packet.getTransaction() instanceof ItemUseInventoryTransaction use
                    && use.getTriggerType() == ItemUseTriggerType.PLAYER_INPUT
                    && inspectClickRate(event, player, playerData, playerData.clickRight(), "right")) {
                return;
            }
            if (packet.getTransaction() instanceof ItemUseOnActorInventoryTransaction attack
                    && attack.getActionType() == ItemUseOnActorActionType.ATTACK) {
                playerData.lastCombatNanos = System.nanoTime();
                if (inspectClickRate(event, player, playerData, playerData.leftCps(), "left")) {
                    return;
                }
            }
            inspectBadSlot(event, player, playerData, packet);
            trackItemUseState(event, player, playerData, packet);
            movementProcessor.trackGlideBoost(player, playerData, packet);
            combatProcessor.inspectAttack(event, player, packet);
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

    private void inspectChestStealer(PacketReceiveEvent event, Player player, PlayerData data,
                                     ItemStackRequestPacket packet, long now) {
        ChestStealerCheck.Result result = chestStealerCheck.inspect(data, packet, now, 8);
        if (result.failed()) {
            fail(event, player, data, CheckType.CHEST_STEALER_A, 1,
                    "cps=" + result.cps(), true);
        }
    }

    private void inspectAutoTotem(PacketReceiveEvent event, Player player, PlayerData data,
                                  ItemStackRequestPacket packet, long now) {
        AutoTotemCheck.Result result = autoTotemCheck.inspect(data, packet, now);
        if (result.failed()) {
            fail(event, player, data, CheckType.AUTO_TOTEM_A, 1,
                    "ms=" + result.elapsedMs(), true);
        }
    }

    private void inspectMacro(Player player, PlayerData data, long now) {
        if (data.macroIntervalCount() < 30) return;
        if (now - data.lastMacroAnalysisNanos < 1_000_000_000L) return;
        if (data.inGrace() || player.hasPermission("amethyst.bypass")) return;
        data.lastMacroAnalysisNanos = now;
        long[] intervals = data.macroIntervalSnapshot();
        MacroCheck.Result result = macroCheck.analyse(intervals, intervals.length,
                data.macroCombatClicks, data.macroTotalClicks);
        if (result.flagged()) {
            data.macroBuffer += result.score();
            if (data.macroBuffer >= 12.0) {
                double vl = data.violations.merge(CheckType.MACRO_A.id(), 1.0, Double::sum);
                plugin.alert(player, CheckType.MACRO_A, vl,
                        "score=" + NetworkCheckSupport.format(result.score()) + " " + result.breakdown());
                data.macroBuffer = 6.0;
            }
            data.macroSuspectStreak = 0;
        } else if (result.suspect()) {
            data.macroSuspectStreak++;
            if (data.macroSuspectStreak >= 3) {
                data.macroBuffer += result.score();
                if (data.macroBuffer >= 12.0) {
                    double vl = data.violations.merge(CheckType.MACRO_A.id(), 1.0, Double::sum);
                    plugin.alert(player, CheckType.MACRO_A, vl,
                            "score=" + NetworkCheckSupport.format(result.score()) + " " + result.breakdown());
                    data.macroBuffer = 6.0;
                }
            }
        } else {
            data.macroBuffer = Math.max(0, data.macroBuffer - 1.0);
            data.macroSuspectStreak = Math.max(0, data.macroSuspectStreak - 1);
        }
        if (data.macroTotalClicks > 200) {
            data.resetMacroCorrelation();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSend(PacketSendEvent event) {
        if (players.isEmpty()) return;
        Player player = event.getPlayer();
        if (player == null) return;
        PlayerData data = players.get(player.getUniqueId());
        if (data == null) return;
        if (event.getPacket() instanceof ActorEventPacket actorEvent
                && actorEvent.getType() == ActorEvent.TALISMAN_ACTIVATE
                && actorEvent.getTargetRuntimeID() == player.getId()) {
            data.lastTotemPopNanos = System.nanoTime();
            return;
        }
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
                    true, packet.getActorData(), null, false)) {
                flushEntityTrackerAfter(event, player, data);
            }
            return;
        }
        if (event.getPacket() instanceof AddActorPacket packet) {
            Entity entity = player.getLevel().getEntity(packet.getTargetRuntimeID());
            if (packet.getTargetRuntimeID() != player.getId()
                    && data.clientEntities.queueAdd(packet.getTargetRuntimeID(), packet.getPosition(),
                    false, packet.getActorData(), packet.getActorType(),
                    entity instanceof EntityProjectile)) {
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
            data.grantGracePeriod(GraceReason.ENTITY_DESPAWN, GRACE_MILLIS);
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
                if (data.meleeKnockbackPending) {
                    data.meleeKnockbackPending = false;
                    if (velocity.x() == 0.0 && velocity.z() == 0.0) {
                        return;
                    }
                    data.expectedMeleeKnockback = velocity;
                    data.meleeKnockbackTicks = 4;
                    data.meleeKnockbackObserved = 0.0;
                    data.meleeKnockbackExpected = 0.0;
                }
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
    public void onMeleeDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)
                || event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || !(event.getDamager() instanceof EntityLiving)) {
            return;
        }
        PlayerData data = players.get(player.getUniqueId());
        if (data == null || !data.joined) {
            return;
        }
        data.meleeKnockbackPending = true;
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
        Vec3 candidate = new Vec3(motion.x, motion.y, motion.z);
        Vec3 lunge = data.spearLungeCandidate();
        if (lunge != null && lunge.distance(candidate) <= 1.0E-6) {
            return;
        }
        data.addServerMotionCandidate(candidate);
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

    public void handleEarlyMovementRejection(Player player, MovementPreValidationResult result) {
        movementProcessor.handleEarlyMovementRejection(player, result);
    }

    private boolean inspectClickRate(PacketReceiveEvent event, Player player, PlayerData data,
                                     int clicks, String hand) {
        int limit = data.touchInput
                ? plugin.settings().combatTouchCpsLimit()
                : plugin.settings().combatCpsLimit();
        if (limit <= 0 || clicks <= limit || data.inGrace()) {
            return false;
        }
        fail(event, player, data, CheckType.AUTOCLICKER_A, 1,
                hand + " cps=" + clicks + " of " + limit, true, false);
        return true;
    }

    private void inspectPlayerAction(PacketReceiveEvent event, Player player, PlayerData data,
                                     PlayerActionPacket packet) {
        PlayerActionType type = packet.getAction();
        if (type == PlayerActionType.STOP_SLEEPING && !data.inBed && !player.isSleeping()
                && !MovementPacketProcessor.recentlySleeping(data)) {
            fail(event, player, data, CheckType.BAD_PACKET_K, 2, "not asleep", true);
            return;
        }
        if (type == PlayerActionType.RESPAWN && player.isAlive() && player.getHealth() > 0) {
            fail(event, player, data, CheckType.BAD_PACKET_L, 2, "still alive", true);
        }
    }

    private void inspectInteract(PacketReceiveEvent event, Player player, PlayerData data,
                                 InteractPacket packet) {
        InteractPacket.Action action = packet.getAction();
        if (action == InteractPacket.Action.OPEN_INVENTORY
                || action == InteractPacket.Action.INVALID) {
            return;
        }
        long target = packet.getTargetRuntimeID();
        if (target == 0 || target == player.getId() || data.inGrace()
                || data.inGrace(GraceReason.ENTITY_DESPAWN)) {
            return;
        }
        if (player.getLevel().getEntity(target) != null
                || data.clientEntities.view(target) != null
                || data.clientEntities.isProjectile(target)) {
            return;
        }
        fail(event, player, data, CheckType.BAD_PACKET_M, 2, "target=" + target, true);
    }

    private void inspectBadSlot(PacketReceiveEvent event, Player player, PlayerData data,
                                InventoryTransactionPacket packet) {
        if (!(packet.getTransaction() instanceof ItemUseInventoryTransaction transaction)) {
            return;
        }
        int slot = transaction.getSlot();
        if (slot >= 0 && slot < HOTBAR_SIZE) {
            return;
        }
        ItemData sent = transaction.getItem();
        if (sent == null || sent.getDefinition() == null) {
            return;
        }
        String used = sent.getDefinition().getIdentifier();
        if (!THROWN_FROM_HOTBAR.contains(used)) {
            return;
        }
        Item offhand = player.getOffhandInventory().getItem(0);
        if (offhand != null && !offhand.isNull() && used.equals(offhand.getId())) {
            return;
        }
        fail(event, player, data, CheckType.BAD_SLOT_A, 1,
                "used " + used + " from slot " + slot, true);
    }

    private void trackItemUseState(PacketReceiveEvent event, Player player, PlayerData data,
                                   InventoryTransactionPacket packet) {
        if (packet.getTransaction() instanceof ItemReleaseInventoryTransaction release) {
            data.motion.consuming(false);
            inspectFastUse(event, player, data, release);
            return;
        }
        if (!(packet.getTransaction() instanceof ItemUseInventoryTransaction transaction)
                || transaction.getActionType() != ItemUseActionType.USE) {
            return;
        }
        Item item = player.getInventory().getItemInMainHand();
        if (item != null && !item.isNull() && item.isConsumable()) {
            data.itemUseStartTick = data.lastTick;
            if (item.getUseDuration() > 0.0f) {
                data.motion.consuming(true);
            }
        }
    }

    private void inspectFastUse(PacketReceiveEvent event, Player player, PlayerData data,
                                ItemReleaseInventoryTransaction release) {
        long start = data.itemUseStartTick;
        data.itemUseStartTick = Long.MIN_VALUE;
        if (start == Long.MIN_VALUE || data.inGrace()
                || release.getActionType() != ItemReleaseActionType.USE) {
            return;
        }

        long ticks = data.lastTick - start;
        if (ticks >= FAST_USE_MINIMUM_TICKS) {
            return;
        }

        fail(event, player, data, CheckType.FAST_USE_A, 1, "ticks=" + ticks, true, false);
    }

    private void fail(Cancellable event, Player player, PlayerData data, CheckType check,
                      double amount, String detail, boolean cancel) {
        fail(event, player, data, check, amount, detail, cancel, true);
    }

    private void fail(Cancellable event, Player player, PlayerData data, CheckType check,
                      double amount, String detail, boolean cancel, boolean setback) {
        if (plugin.settings().disabled(check.id())) {
            return;
        }
        double vl = data.violations.merge(check.id(), amount, Double::sum);
        long now = System.nanoTime();
        if (check == CheckType.AUTOCLICKER_A) {
            plugin.alert(player, check, vl, detail);
        } else if (now - data.lastAlertNanos > 300_000_000L) {
            plugin.alert(player, check, vl, detail);
            data.lastAlertNanos = now;
        }
        if (cancel) event.setCancelled();
        double setbackThreshold = Math.max(1.0, plugin.settings().setbackViolations());
        if (setback && cancel && MovementCheckSupport.isMovementCheck(check) && vl >= setbackThreshold) {
            movementProcessor.scheduleMovementCorrection(player, data);
        }
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

    private void kick(Player player, PlayerData data) {
        kick(player, data, "Invalid packet");
    }

    private void kick(Player player, PlayerData data, String reason) {
        if (data.kickScheduled) {
            return;
        }
        data.kickScheduled = true;
        plugin.getServer().getScheduler().scheduleTask(plugin, () -> {
            if (player.isOnline()) player.kick(reason);
        });
    }

    private PlayerData data(Player player) {
        return players.computeIfAbsent(player.getUniqueId(), ignored -> {
            PlayerData created = new PlayerData();
            created.safeLocation = player.getLocation();
            created.grantGrace(GraceReason.CHUNK_LOADING, GRACE_MILLIS);
            return created;
        });
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
