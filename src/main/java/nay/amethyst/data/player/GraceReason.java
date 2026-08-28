package nay.amethyst.data.player;

/** Identifies the server-side transition protected by a temporary check exemption. */
public enum GraceReason {
    TELEPORT,
    ENTITY_DESPAWN,
    WORLD_CHANGE,
    SERVER_CORRECTION,
    VELOCITY,
    CHUNK_LOADING,
    EFFECT_CHANGE
}
