package nay.amethyst.prediction.combat;

import nay.amethyst.data.player.PlayerData;
import nay.amethyst.history.model.Aabb;
import nay.amethyst.history.model.EntityFrame;
import nay.amethyst.history.model.WorldFrame;
import nay.amethyst.listener.network.support.NetworkCheckSupport;
import nay.amethyst.prediction.common.Vec3;
import nay.amethyst.tracking.entity.ClientEntityView;
import org.powernukkitx.Player;
import org.powernukkitx.entity.Entity;
import org.powernukkitx.math.AxisAlignedBB;
import org.powernukkitx.math.SimpleAxisAlignedBB;

import java.util.List;

public final class CombatPredictor {
    public static final double SURVIVAL_REACH = 3.0;
    private static final double TRACE_LENGTH = 7.0;

    public CombatPredictionResult predict(Player player, PlayerData data, Entity target,
                                          double boxExpansion, double reachLeniency,
                                          int lerpSteps, double maximumAttackAngle,
                                          double closeRangeFallback, double closeRangeAngle,
                                          boolean rawDistanceFallback) {
        long latency = Math.max(NetworkCheckSupport.ping(player), data.network.stackLatencyMillis());
        int frameCount = Math.max(2, Math.min(12, (int) Math.ceil(latency / 50.0) + 2));
        List<WorldFrame> frames = data.history.recent(frameCount);
        ClientEntityView clientEntity = data.clientEntities.view(target.getId());
        Candidate best = null;
        if (clientEntity != null && clientEntity.player() && clientEntity.ticksSinceTeleport() > 10
                && frames.size() >= 2) {
            Candidate client = predictClientView(frames.get(1), frames.get(0), clientEntity,
                    boxExpansion, reachLeniency, lerpSteps, maximumAttackAngle, rawDistanceFallback);
            best = better(best, client);
            if (client != null && client.valid) return client.result();
        }

        for (int index = 0; index + 1 < frames.size(); index++) {
            WorldFrame end = frames.get(index);
            WorldFrame start = frames.get(index + 1);
            EntityFrame startEntity = start.entities().get(target.getId());
            EntityFrame endEntity = end.entities().get(target.getId());
            if (startEntity == null || endEntity == null || !start.levelName().equals(end.levelName())) continue;

            for (int step = 0; step <= lerpSteps; step++) {
                double partial = step / (double) lerpSteps;
                Vec3 eye = lerpEye(start, end, partial);
                float yaw = lerpAngle(start.yaw(), end.yaw(), partial);
                float pitch = (float) lerp(start.pitch(), end.pitch(), partial);
                Aabb box = lerp(startEntity.box(), endEntity.box(), partial).expand(boxExpansion);
                Candidate candidate = trace(eye, look(yaw, pitch), box, reachLeniency,
                        maximumAttackAngle, closeRangeFallback, closeRangeAngle, rawDistanceFallback);
                best = better(best, candidate);
                if (candidate.valid) return candidate.result();
            }
        }

        WorldFrame latest = data.history.latest();
        Vec3 eye = currentEye(player, data, latest);
        Vec3 direction = latest == null ? look((float) player.yaw, (float) player.pitch)
                : look(latest.yaw(), latest.pitch());
        Candidate current = trace(eye, direction,
                Aabb.from(target.getBoundingBox()).expand(boxExpansion), reachLeniency,
                maximumAttackAngle, closeRangeFallback, closeRangeAngle, rawDistanceFallback);
        best = better(best, current);
        return best == null ? invalidResult() : best.result();
    }

    private static Candidate predictClientView(WorldFrame start, WorldFrame end,
                                               ClientEntityView entity,
                                               double boxExpansion, double reachLeniency,
                                               int lerpSteps, double maximumAttackAngle,
                                               boolean rawDistanceFallback) {
        double halfX = entity.width() * entity.scale() / 2.0;
        double halfZ = halfX;
        double height = entity.height() * entity.scale();
        Candidate best = null;

        for (int step = 0; step <= lerpSteps; step++) {
            double partial = step / (double) lerpSteps;
            Vec3 eye = lerpEye(start, end, partial);
            float yaw = lerpAngle(start.yaw(), end.yaw(), partial);
            float pitch = (float) lerp(start.pitch(), end.pitch(), partial);
            Vec3 position = lerp(entity.previousPosition(), entity.position(), partial);
            Aabb box = new Aabb(position.x() - halfX, position.y(), position.z() - halfZ,
                    position.x() + halfX, position.y() + height, position.z() + halfZ).expand(boxExpansion);
            Candidate candidate = trace(eye, look(yaw, pitch), box, reachLeniency,
                    maximumAttackAngle, 0, 0, rawDistanceFallback);
            best = better(best, candidate);
            if (candidate.valid) return candidate;
        }
        return best;
    }

    private static Candidate trace(Vec3 eye, Vec3 direction, Aabb box,
                                   double reachLeniency, double maximumAttackAngle,
                                   double closeRangeFallback, double closeRangeAngle,
                                   boolean rawDistanceFallback) {
        double raw = box.distanceTo(eye.x(), eye.y(), eye.z());
        double angle = angleToBox(eye, direction, box);
        double ray = box.rayDistance(eye, direction, TRACE_LENGTH);
        boolean raycastHit = Double.isFinite(ray);
        boolean inReach = raycastHit && ray <= SURVIVAL_REACH + reachLeniency;
        boolean closeFallback = raw <= closeRangeFallback && angle <= closeRangeAngle;
        if (!raycastHit && (rawDistanceFallback || closeFallback)
                && raw <= SURVIVAL_REACH && angle <= maximumAttackAngle) {
            ray = raw;
            raycastHit = true;
            inReach = true;
        }
        boolean valid = inReach && angle <= maximumAttackAngle;
        return new Candidate(valid, raycastHit, ray, raw, angle);
    }

    private static Candidate better(Candidate first, Candidate second) {
        if (second == null) return first;
        if (first == null) return second;
        if (second.valid != first.valid) return second.valid ? second : first;
        if (second.rawDistance != first.rawDistance) {
            return second.rawDistance < first.rawDistance ? second : first;
        }
        return second.angle < first.angle ? second : first;
    }

    private static Vec3 lerpEye(WorldFrame start, WorldFrame end, double partial) {
        Vec3 startEye = start.position().add(0,
                -start.physics().baseOffset() + start.physics().eyeHeight(), 0);
        Vec3 endEye = end.position().add(0,
                -end.physics().baseOffset() + end.physics().eyeHeight(), 0);
        return lerp(startEye, endEye, partial);
    }

    private static Vec3 currentEye(Player player, PlayerData data, WorldFrame latest) {
        if (latest != null) {
            return latest.position().add(0,
                    -latest.physics().baseOffset() + latest.physics().eyeHeight(), 0);
        }
        if (data.lastPosition != null) {
            return new Vec3(data.lastPosition.getX(),
                    data.lastPosition.getY() - player.getBaseOffset() + player.getEyeHeight(),
                    data.lastPosition.getZ());
        }
        return new Vec3(player.x, player.y + player.getEyeHeight(), player.z);
    }

    private static Aabb lerp(Aabb start, Aabb end, double partial) {
        return new Aabb(lerp(start.minX(), end.minX(), partial), lerp(start.minY(), end.minY(), partial),
                lerp(start.minZ(), end.minZ(), partial), lerp(start.maxX(), end.maxX(), partial),
                lerp(start.maxY(), end.maxY(), partial), lerp(start.maxZ(), end.maxZ(), partial));
    }

    private static Vec3 lerp(Vec3 start, Vec3 end, double partial) {
        return new Vec3(lerp(start.x(), end.x(), partial), lerp(start.y(), end.y(), partial),
                lerp(start.z(), end.z(), partial));
    }

    private static double lerp(double start, double end, double partial) {
        return start + (end - start) * partial;
    }

    private static float lerpAngle(float start, float end, double partial) {
        double delta = ((end - start + 540.0) % 360.0) - 180.0;
        return (float) (start + delta * partial);
    }

    private static double angleToBox(Vec3 origin, Vec3 direction, Aabb box) {
        double x = clamp(origin.x(), box.minX(), box.maxX()) - origin.x();
        double y = clamp(origin.y(), box.minY(), box.maxY()) - origin.y();
        double z = clamp(origin.z(), box.minZ(), box.maxZ()) - origin.z();
        double length = Math.sqrt(x * x + y * y + z * z);
        if (length < 1.0E-9) return 0;
        double dot = (direction.x() * x + direction.y() * y + direction.z() * z) / length;
        return Math.toDegrees(Math.acos(clamp(dot, -1, 1)));
    }

    private static Vec3 look(float yaw, float pitch) {
        double yawRadians = Math.toRadians(yaw);
        double pitchRadians = Math.toRadians(pitch);
        double cosine = Math.cos(pitchRadians);
        return new Vec3(-Math.sin(yawRadians) * cosine, -Math.sin(pitchRadians),
                Math.cos(yawRadians) * cosine);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static CombatPredictionResult invalidResult() {
        return new CombatPredictionResult(false, false, Double.POSITIVE_INFINITY,
                Double.POSITIVE_INFINITY, 180);
    }

    private record Candidate(boolean valid, boolean raycastHit, double rayDistance,
                             double rawDistance, double angle) {
        private CombatPredictionResult result() {
            return new CombatPredictionResult(valid, raycastHit, rayDistance, rawDistance, angle);
        }
    }
}
