package nay.amethyst.check.type;

public enum CheckType {
    BAD_PACKET_A("BadPacket-A", "Non-finite movement values or impossible rotation."),
    BAD_PACKET_B("BadPacket-B", "Movement sent on a stale or repeated frame."),
    BAD_PACKET_C("BadPacket-C", "Invalid action sequence or vehicle state."),
    BAD_PACKET_D("BadPacket-D", "Client tick returned to zero after startup."),
    BAD_PACKET_E("BadPacket-E", "Attack sent against the player's own runtime ID."),
    BAD_PACKET_F("BadPacket-F", "Block destruction sent through an invalid channel."),
    BAD_PACKET_G("BadPacket-G", "Creative crafting request sent outside creative mode."),
    BAD_PACKET_H("BadPacket-H", "Movement vector component outside valid bounds."),
    BAD_PACKET_I("BadPacket-I", "Invalid slot, trigger, or client prediction value."),
    BAD_PACKET_J("BadPacket-J", "Invalid block face for the submitted action."),
    BAD_PACKET_K("BadPacket-K", "Woke up without being asleep."),
    BAD_PACKET_L("BadPacket-L", "Respawned while still alive."),
    BAD_PACKET_M("BadPacket-M", "Interacted with an entity that does not exist."),
    BAD_PACKET_N("BadPacket-N", "A block was placed against something the player was not looking at."),
    BAD_PACKET_O("BadPacket-O", "The predicted vehicle is not the one being ridden."),
    BAD_PACKET_P("BadPacket-P", "Position outside the bounds of the world."),
    BAD_PACKET_Q("BadPacket-Q", "Chunk radius outside the accepted range."),
    AUTOCLICKER_A("Autoclicker-A", "Clicked faster than a hand can."),
    INV_MOVE_A("InvMove-A", "Directed movement during an inventory interaction."),
    TIMER("Timer", "The client sent more frames than its network credit allows."),
    SIMULATION("Simulation", "The server corrected movement that did not match its simulation.", true),
    VEHICLE_A("Vehicle-A", "Vehicle movement did not match the simulation.", true),
    NO_FALL_A("NoFall-A", "Fall damage did not match the simulated fall.", true),
    VELOCITY_A("Velocity-A", "A server impulse was missing from received movement.", true),
    KILL_AURA_A("KillAura-A", "Invalid attack target or attack sequence.", true),
    REACH_A("Reach-A", "The target was attacked beyond the allowed reach.", true),
    HITBOX_A("Hitbox-A", "The sight ray did not intersect the target hitbox.", true),
    BREAK_REACH("BreakReach-A", "A block was broken beyond the allowed reach."),
    PLACE_REACH_A("PlaceReach-A", "A block was placed beyond the allowed reach."),
    FAST_BREAK_A("FastBreak-A", "A block was destroyed before its calculated break time."),
    SCAFFOLD_A("Scaffold-A", "The click vector was zero during an initial player-input placement."),
    GROUND_SPOOF_A("GroundSpoof-A", "The client reported standing on nothing.", true),
    FAST_USE_A("FastUse-A", "An item was consumed faster than it can be used."),
    BEDROCK_TOOL_A("BedrockTool-A", "The client identity matches a known tool.", true),
    BAD_SLOT_A("BadSlot-A", "An item was used from a slot outside the hotbar."),
    COBWEB_A("Cobweb-A", "Movement through a cobweb faster than it allows.", true),
    SPRINT_A("Sprint-A", "Sprinting on too little food."),
    SPRINT_B("Sprint-B", "Sprinting while using an item."),
    SPRINT_C("Sprint-C", "Started sprinting while blinded."),
    ELYTRA_A("Elytra-A", "Started gliding while riding."),
    ELYTRA_B("Elytra-B", "Started gliding again too soon.");

    private final String id;
    private final String description;
    private final boolean experimental;

    CheckType(String id, String description) {
        this(id, description, false);
    }

    CheckType(String id, String description, boolean experimental) {
        this.id = id;
        this.description = description;
        this.experimental = experimental;
    }

    public String id() {
        return id;
    }

    public String description() {
        return description;
    }

    public boolean experimental() {
        return experimental;
    }
}
