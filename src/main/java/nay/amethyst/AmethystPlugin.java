package nay.amethyst;

import nay.amethyst.api.PlayerViolationEvent;
import nay.amethyst.data.player.PlayerData;
import nay.amethyst.check.type.CheckType;
import nay.amethyst.config.AmethystSettings;
import nay.amethyst.diagnostics.AlertFormatter;
import nay.amethyst.listener.network.PacketListener;
import nay.amethyst.network.session.MovementSessionRegistry;
import nay.amethyst.update.VersionChecker;
import org.powernukkitx.Player;
import org.powernukkitx.command.Command;
import org.powernukkitx.command.CommandSender;
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
        getServer().getScheduler().scheduleRepeatingTask(this, listener::onServerTick, 1);
        getLogger().info("Amethyst is enabled don't worry about hackers");
        if (getConfig().getBoolean("updates.check", true)) VersionChecker.checkAsync(this);
    }

    @Override
    public void onDisable() {
        if (movementSessions != null) {
            movementSessions.shutdown(getServer().getOnlinePlayers().values());
        }
        players.clear();
        instance = null;
    }

    void loadSettings() {
        reloadConfig();
        alertsEnabled = getConfig().getBoolean("alerts", true);
        settings = AmethystSettings.load(getConfig());
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
        if (settings().disabled(check.id())) return;
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

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("amethyst")) return false;
        if (!sender.hasPermission("amethyst.alerts")) {
            sender.sendMessage("You do not have permission to use this command.");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            sender.sendMessage("Amethyst 1.0.5 | tracked=" + players.size() + " | alerts=" + alertsEnabled);
            sender.sendMessage(memoryStatus());
            return true;
        }
        if (args[0].equalsIgnoreCase("gc")) {
            sender.sendMessage("before-gc " + memoryStatus());
            System.gc();
            getServer().getScheduler().scheduleDelayedTask(this,
                    () -> sender.sendMessage("after-gc " + memoryStatus()), 20);
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            loadSettings();
            sender.sendMessage("Amethyst configuration reloaded.");
            return true;
        }
        if (args[0].equalsIgnoreCase("alerts")) {
            toggleAlerts();
            sender.sendMessage("Amethyst alerts=" + alertsEnabled);
            return true;
        }
        return false;
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
