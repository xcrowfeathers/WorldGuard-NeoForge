package de.kuro.worldguard.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.Event;

public final class ProtectionDecisionEvent extends Event {
    public enum Action {
        BREAK, PLACE, USE, INVENTORY, ITEM_ON_BLOCK, BUCKET_FILL, BUCKET_EMPTY,
        IGNITE, BONE_MEAL, TRAMPLE, ENTITY_INTERACT, ENTITY_DAMAGE, PVP,
        RIDE, VEHICLE_PLACE, VEHICLE_DESTROY, ITEM_PICKUP, ITEM_DROP, EXP_DROP
    }

    public enum Capability { BREAK, PLACE, INTERACT, CONTAINER, ENTITY_INTERACT, OTHER }

    private final ServerPlayer actor;
    private final ServerLevel level;
    private final BlockPos pos;
    private final Action action;
    private final String flagName;
    private final Entity targetEntity;
    private final boolean worldGuardAllowed;
    private boolean allowed;
    private final java.util.function.Supplier<com.sk89q.worldguard.protection.ApplicableRegionSet> regions;

    public ProtectionDecisionEvent(ServerPlayer actor, ServerLevel level, BlockPos pos,
                                   Action action, String flagName, boolean worldGuardAllowed) {
        this(actor, level, pos, action, flagName, worldGuardAllowed, null);
    }

    public ProtectionDecisionEvent(ServerPlayer actor, ServerLevel level, BlockPos pos,
                                   Action action, String flagName, boolean worldGuardAllowed,
                                   Entity targetEntity) {
        this(actor, level, pos, action, flagName, worldGuardAllowed, targetEntity, null);
    }

    public ProtectionDecisionEvent(ServerPlayer actor, ServerLevel level, BlockPos pos,
            Action action, String flagName, boolean worldGuardAllowed, Entity targetEntity,
            java.util.function.Supplier<com.sk89q.worldguard.protection.ApplicableRegionSet> regions) {
        this.regions = regions;
        this.actor = actor;
        this.level = level;
        this.pos = pos.immutable();
        this.action = action;
        this.flagName = flagName;
        this.targetEntity = targetEntity;
        this.worldGuardAllowed = worldGuardAllowed;
        this.allowed = worldGuardAllowed;
    }

    public com.sk89q.worldguard.protection.ApplicableRegionSet getApplicableRegions() { return regions == null ? null : regions.get(); }

    public ServerPlayer getActor() { return actor; }
    public ServerLevel getLevel() { return level; }
    public BlockPos getPos() { return pos; }
    public Action getAction() { return action; }
    public Capability getCapability() {
        return switch (action) {
            case BREAK, TRAMPLE -> Capability.BREAK;
            case PLACE, VEHICLE_PLACE -> Capability.PLACE;
            case USE, ITEM_ON_BLOCK, BUCKET_FILL, BUCKET_EMPTY, IGNITE, BONE_MEAL -> Capability.INTERACT;
            case INVENTORY -> Capability.CONTAINER;
            case ENTITY_INTERACT, RIDE -> Capability.ENTITY_INTERACT;
            default -> Capability.OTHER;
        };
    }
    public String getFlagName() { return flagName; }
    public Entity getTargetEntity() { return targetEntity; }
    public boolean isWorldGuardAllowed() { return worldGuardAllowed; }
    public boolean isAllowed() { return allowed; }

    public void setAllowed(boolean allowed) { this.allowed = allowed; }
}
