package nay.amethyst.network.session;

import io.netty.channel.EventLoop;
import nay.amethyst.AmethystPlugin;
import nay.amethyst.packet.movement.MovementPreValidationResult;
import nay.amethyst.packet.movement.MovementPreValidator;
import nay.amethyst.tracking.network.NetworkTimeline;
import org.cloudburstmc.protocol.bedrock.BedrockServerSession;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacketHandler;
import org.powernukkitx.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;

public final class MovementSessionRegistry {
    private final AmethystPlugin plugin;
    private final Map<UUID, MovementSessionInterceptor> interceptors = new ConcurrentHashMap<>();
    private volatile BiConsumer<Player, MovementPreValidationResult> rejectionHandler = (player, result) -> { };
    private volatile Function<Player, NetworkTimeline> timelineProvider = player -> null;

    public MovementSessionRegistry(AmethystPlugin plugin) {
        this.plugin = plugin;
    }

    public void setRejectionHandler(BiConsumer<Player, MovementPreValidationResult> rejectionHandler) {
        this.rejectionHandler = rejectionHandler;
    }

    public void setTimelineProvider(Function<Player, NetworkTimeline> timelineProvider) {
        this.timelineProvider = timelineProvider;
    }

    public void install(Player player) {
        BedrockServerSession session = player.getSession();
        runOnSession(session, () -> {
            BedrockPacketHandler current = session.getPacketHandler();
            if (current == null) return;
            if (current instanceof MovementSessionInterceptor interceptor) {
                interceptors.put(player.getUniqueId(), interceptor);
                return;
            }
            MovementPreValidator validator = new MovementPreValidator(() -> {
                NetworkTimeline timeline = timelineProvider.apply(player);
                if (timeline != null) {
                    timeline.acceptInput();
                }
            });
            validator.reset();
            MovementSessionInterceptor interceptor = new MovementSessionInterceptor(
                    current,
                    validator,
                    result -> plugin.getServer().getScheduler().scheduleTask(plugin,
                            () -> rejectionHandler.accept(player, result))
            );
            interceptors.put(player.getUniqueId(), interceptor);
            session.setPacketHandler(interceptor);
        });
    }

    public void reset(Player player) {
        MovementSessionInterceptor interceptor = interceptors.get(player.getUniqueId());
        if (interceptor == null) return;
        runOnSession(player.getSession(),
                () -> interceptor.validator().reset());
    }

    public void restore(Player player) {
        MovementSessionInterceptor interceptor = interceptors.remove(player.getUniqueId());
        if (interceptor == null) return;
        BedrockServerSession session = player.getSession();
        runOnSession(session, () -> {
            if (session.getPacketHandler() == interceptor) {
                session.setPacketHandler(interceptor.original());
            }
        });
    }

    public void shutdown(Iterable<Player> players) {
        for (Player player : players) restore(player);
        interceptors.clear();
    }

    private static void runOnSession(BedrockServerSession session, Runnable action) {
        EventLoop eventLoop = session.getPeer().getChannel().eventLoop();
        if (eventLoop.inEventLoop()) action.run();
        else eventLoop.execute(action);
    }
}
