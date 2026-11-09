package nay.amethyst;

import nay.amethyst.api.PlayerViolationEvent;
import nay.amethyst.check.CheckType;
import nay.amethyst.network.MovementSessionRegistry;
import nay.amethyst.network.PacketListener;
import nay.amethyst.player.PlayerData;
import org.powernukkitx.Player;
import org.powernukkitx.command.Command;
import org.powernukkitx.command.CommandSender;
import org.powernukkitx.command.PluginCommand;
import org.powernukkitx.command.data.CommandParameter;
import org.cloudburstmc.protocol.bedrock.data.command.CommandParamType;
import org.powernukkitx.event.Event;
import org.powernukkitx.event.EventHandler;
import org.powernukkitx.event.Listener;
import org.powernukkitx.plugin.MethodEventExecutor;
import org.powernukkitx.plugin.PluginBase;

import java.util.Map;
import java.util.UUID;
import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;
import java.util.concurrent.ConcurrentHashMap;

public final class AmethystPlugin extends PluginBase {
    private final Map<UUID, PlayerData> players = new ConcurrentHashMap<>();
    private PacketListener listener;
    private MovementSessionRegistry movementSessions;
    private boolean alertsEnabled;
    private volatile AmethystSettings settings;
    private final AlertFormatter alertFormatter = new AlertFormatter();
    private final ContainerConcealer concealer = new ContainerConcealer();
    private int concealTicks;

    private static AmethystPlugin instance;

    public static AmethystPlugin getInstance() {
        return instance;
    }

    public PlayerData getPlayerData(Player player) {
        return player == null ? null : players.get(player.getUniqueId());
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        loadSettings();
        movementSessions = new MovementSessionRegistry(this);
        listener = new PacketListener(this, players, movementSessions);
        movementSessions.setRejectionHandler(listener::handleEarlyMovementRejection);
        movementSessions.setTimelineProvider(listener::networkTimeline);
        registerEvents(listener);
        registerCommandParameters();
        getServer().getScheduler().scheduleRepeatingTask(this, listener::onServerTick, 1);
        getServer().getScheduler().scheduleRepeatingTask(this, this::concealContainers, 1);
        getLogger().info("Amethyst is enabled don't worry about hackers");
        if (getConfig().getBoolean("updates.check", true)) VersionChecker.checkAsync(this);
    }

    @Override
    public void onDisable() {
        if (movementSessions != null) {
            movementSessions.shutdown(getServer().getOnlinePlayers().values());
        }
        for (Player player : getServer().getOnlinePlayers().values()) {
            concealer.reveal(player);
        }
        concealer.clear();
        players.clear();
        instance = null;
    }

    void loadSettings() {
        reloadConfig();
        alertsEnabled = getConfig().getBoolean("alerts", true);
        boolean concealed = settings != null && settings.concealContainers();
        settings = AmethystSettings.load(getConfig());
        if (concealed && !settings.concealContainers()) {
            for (Player player : getServer().getOnlinePlayers().values()) {
                concealer.reveal(player);
            }
        }
    }

    public ContainerConcealer concealer() {
        return concealer;
    }

    private void concealContainers() {
        AmethystSettings current = settings;
        if (current == null || !current.concealContainers()
                || ++concealTicks % Math.max(1, current.concealInterval()) != 0) {
            return;
        }
        for (Player player : getServer().getOnlinePlayers().values()) {
            if ((player.isSurvival() || player.isAdventure()) && !player.hasPermission("amethyst.bypass")) {
                concealer.refresh(player, current);
            } else {
                concealer.reveal(player);
            }
        }
    }

    public AmethystSettings settings() {
        return settings;
    }

    public boolean alertsEnabled() {
        return alertsEnabled;
    }

    public void toggleAlerts() {
        alertsEnabled = !alertsEnabled;
    }

    public void alert(Player suspect, CheckType check, double vl, String detail) {
        if (settings().disabledCheck(check.id())) return;
        PlayerViolationEvent event = new PlayerViolationEvent(suspect, check, vl, detail);
        getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) return;
        if (!alertsEnabled) return;
        String shown = settings().devLogs() ? detail : "";
        String message = alertFormatter.format(suspect, check, vl, shown);
        getLogger().warning("[Amethyst] " + suspect.getName() + " failed " + check.id()
                + " (VL " + String.format("%.1f", vl) + ")"
                + (shown.isEmpty() ? "" : " " + shown));
        for (Player player : getServer().getOnlinePlayers().values()) {
            if (player.hasPermission("amethyst.alerts")) player.sendMessage(message);
        }
    }

    @SuppressWarnings("unchecked")
    private void registerEvents(Listener target) {
        for (var method : target.getClass().getMethods()) {
            EventHandler handler = method.getAnnotation(EventHandler.class);
            if (handler == null || method.getParameterCount() != 1
                    || !Event.class.isAssignableFrom(method.getParameterTypes()[0])) continue;
            getServer().getPluginManager().registerEvent(
                    (Class<? extends Event>) method.getParameterTypes()[0],
                    target,
                    handler.priority(),
                    new MethodEventExecutor(method),
                    this,
                    handler.ignoreCancelled());
        }
    }

    private static final String PREFIX = "§u[Amethyst] §f";

    private void registerCommandParameters() {
        PluginCommand<?> command = getPluginCommand("amethyst");
        if (command == null) return;
        command.getCommandParameters().clear();
        command.addCommandParameters("action", new CommandParameter[]{
                CommandParameter.newEnum("action", true, new String[]{"status", "reload", "alerts"})});
        command.addCommandParameters("data", new CommandParameter[]{
                CommandParameter.newEnum("data", new String[]{"data"}),
                CommandParameter.newType("player", true, CommandParamType.SELECTION)});
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("amethyst")) return false;
        if (!sender.hasPermission("amethyst.alerts")) {
            sender.sendMessage(PREFIX + "§cYou are not allowed to use this.");
            return true;
        }
        String action = args.length == 0 ? "status" : args[0].toLowerCase(java.util.Locale.ROOT);
        switch (action) {
            case "status" -> sender.sendMessage(PREFIX + "v" + getDescription().getVersion()
                    + " §7· §f" + players.size() + " §7tracked · alerts " + onOff(alertsEnabled));
            case "reload" -> {
                loadSettings();
                sender.sendMessage(PREFIX + "Configuration reloaded.");
            }
            case "alerts" -> {
                toggleAlerts();
                sender.sendMessage(PREFIX + "Alerts " + onOff(alertsEnabled));
            }
            case "data" -> sendData(sender, args);
            case "gc" -> {
                sender.sendMessage(PREFIX + "§7before §f" + memoryStatus());
                System.gc();
                getServer().getScheduler().scheduleDelayedTask(this,
                        () -> sender.sendMessage(PREFIX + "§7after §f" + memoryStatus()), 20);
            }
            default -> sender.sendMessage(PREFIX + "§7/amethyst §f[status|reload|alerts|data <player>]");
        }
        return true;
    }

    private static String onOff(boolean value) {
        return value ? "§aon" : "§coff";
    }

    /** What the data-gathering checks hold on a player, the sender when none is named. */
    private void sendData(CommandSender sender, String[] args) {
        Player target = args.length >= 2 ? getServer().getPlayer(args[1])
                : sender instanceof Player self ? self : null;
        PlayerData data = getPlayerData(target);
        if (data == null) {
            sender.sendMessage(PREFIX + "§cNo tracked player by that name.");
            return;
        }
        var hitbox = data.hitbox.snapshot();
        var backtrack = data.backtrack.snapshot();
        sender.sendMessage(PREFIX + target.getName());
        sender.sendMessage(String.format(java.util.Locale.ROOT,
                " §uHitbox-B §f%d §7hits · point avg §f%.3f §7max §f%.3f §7· over 0.1 §f%d §7· origin avg §f%.3f §7max §f%.3f",
                hitbox.hits(), hitbox.clickAverage(), hitbox.clickMax(), hitbox.beyondTenth(),
                hitbox.originAverage(), hitbox.originMax()));
        sender.sendMessage(String.format(java.util.Locale.ROOT,
                " §uBacktrack-B §f%d §7hits · §f%d §7receding · §f%d §7late (§f%.0f%%§7) · delay §f%.0fms §7max §f%.0fms §7· gain §f%.2f",
                backtrack.hits(), backtrack.receding(), backtrack.late(), backtrack.ratio() * 100.0,
                backtrack.delayMillis(), backtrack.longestDelayMillis(), backtrack.gain()));
    }

    private static long mib(long bytes) {
        return bytes / (1024 * 1024);
    }

    private String memoryStatus() {
        Runtime runtime = Runtime.getRuntime();
        long used = runtime.totalMemory() - runtime.freeMemory();
        long direct = ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class).stream()
                .filter(pool -> pool.getName().equals("direct"))
                .mapToLong(BufferPoolMXBean::getMemoryUsed).sum();
        int frames = players.values().stream().mapToInt(data -> data.history.size()).sum();
        int blocks = players.values().stream().mapToInt(data -> data.clientWorld.retainedBlockCount()).sum();
        int entities = players.values().stream().mapToInt(data -> data.clientEntities.retainedEntityCount()).sum();
        return "heap-used=" + mib(used) + "MiB heap-committed=" + mib(runtime.totalMemory())
                + "MiB direct=" + mib(direct) + "MiB frames=" + frames
                + " blocks=" + blocks + " entities=" + entities;
    }
}
