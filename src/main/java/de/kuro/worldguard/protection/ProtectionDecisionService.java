package de.kuro.worldguard.protection;

import com.sk89q.worldedit.util.Location;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import de.kuro.worldguard.api.ProtectionDecisionEvent;
import de.kuro.worldguard.platform.NeoForgeWorldGuardPlatform;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.NeoForge;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ProtectionDecisionService {
    private static final long MESSAGE_INTERVAL_NANOS = 500_000_000L;
    private final NeoForgeWorldGuardPlatform platform;
    private final Map<UUID, Long> lastMessage = new HashMap<>();

    public ProtectionDecisionService(NeoForgeWorldGuardPlatform platform) {
        this.platform = platform;
    }

    public boolean active() {
        return platform.getRegionContainer() != null;
    }

    public void forget(ServerPlayer player) {
        lastMessage.remove(player.getUUID());
    }

    public void reset() {
        lastMessage.clear();
    }

    public Context context(ServerPlayer player, ServerLevel level) {
        LocalPlayer local = platform.adapt(player);
        if (platform.getSessionManager().getIfPresent(local) == null) {
            platform.getSessionManager().get(local);
        }
        World world = platform.adaptWorld(level);
        boolean bypass = platform.getSessionManager().hasBypass(local, world);
        return new Context(player, level, local, world,
                platform.getRegionContainer().createQuery(), bypass);
    }

    /** Memoize only within one synchronous action, never across region mutations/actions. */
    private static final class ActionQuery extends RegionQuery {
        private final RegionQuery delegate;
        private final Map<Location, com.sk89q.worldguard.protection.ApplicableRegionSet> regions = new HashMap<>();
        ActionQuery(RegionQuery delegate) { super(new com.sk89q.worldguard.protection.regions.QueryCache()); this.delegate = delegate; }
        void clear() { regions.clear(); }
        @Override public com.sk89q.worldguard.protection.ApplicableRegionSet getApplicableRegions(Location location) {
            return regions.computeIfAbsent(location, delegate::getApplicableRegions);
        }
    }
    public final class Context {
        private final ServerPlayer player;
        private final ServerLevel level;
        private final LocalPlayer local;
        private final World world;
        private final ActionQuery query;
        private final boolean bypass;

        private Context(ServerPlayer player, ServerLevel level, LocalPlayer local, World world,
                        RegionQuery query, boolean bypass) {
            this.player = player;
            this.level = level;
            this.local = local;
            this.world = world;
            this.query = new ActionQuery(query);
            this.bypass = bypass;
        }

        public boolean bypasses() { return bypass; }

        public boolean check(BlockPos pos, ProtectionDecisionEvent.Action action,
                             StateFlag[] flags, String what) {
            query.clear();
            Location location = location(pos);
            return decide(pos, null, action, flags, what, location, true,
                    bypass || query.testBuild(location, local, flags));
        }

        public boolean check(Entity target, ProtectionDecisionEvent.Action action,
                             StateFlag[] flags, String what) {
            BlockPos pos = target.blockPosition();
            query.clear();
            Location location = location(pos);
            return decide(pos, target, action, flags, what, location, true,
                    bypass || query.testBuild(location, local, flags));
        }

        /** Upstream exempts hostile/ambient targets and vehicles from implicit BUILD. */
        public boolean checkExplicit(Entity target, ProtectionDecisionEvent.Action action,
                                     StateFlag[] flags, String what) {
            BlockPos pos = target.blockPosition();
            query.clear();
            Location location = location(pos);
            boolean allowed = bypass || flags.length == 0
                    || query.queryState(location, local, flags) != StateFlag.State.DENY;
            return decide(pos, target, action, flags, what, location, false, allowed);
        }

        /** Bukkit deliberately applies PvP even to players with the region bypass permission. */
        public boolean checkPvp(ServerPlayer defender, StateFlag[] flags) {
            BlockPos pos = defender.blockPosition();
            query.clear();
            Location target = location(pos);
            boolean allowed = query.testBuild(target, local, flags)
                    && query.queryState(new Location(platform.adaptWorld(player.serverLevel()),
                            player.getBlockX(), player.getBlockY(), player.getBlockZ()),
                            local, flags) != StateFlag.State.DENY
                    && query.queryState(target, local, flags) != StateFlag.State.DENY;
            return decide(pos, defender, ProtectionDecisionEvent.Action.PVP,
                    flags, "PvP", target, true, allowed);
        }

        private Location location(BlockPos pos) {
            return new Location(world, pos.getX(), pos.getY(), pos.getZ());
        }

        private boolean decide(BlockPos pos, Entity targetEntity, ProtectionDecisionEvent.Action action,
                               StateFlag[] flags, String what, Location location,
                               boolean implicitBuild, boolean allowed) {
            ProtectionDecisionEvent decision = new ProtectionDecisionEvent(player, level, pos,
                    action, flags.length == 0
                            ? implicitBuild ? Flags.BUILD.getName() : ""
                            : flags[flags.length - 1].getName(),
                    allowed, targetEntity, () -> query.getApplicableRegions(location));
            NeoForge.EVENT_BUS.post(decision);
            if (!decision.isAllowed()) feedback(location, what);
            return decision.isAllowed();
        }

        private void feedback(Location location, String what) {
            long now = System.nanoTime();
            Long last = lastMessage.get(player.getUUID());
            if (last != null && now - last < MESSAGE_INTERVAL_NANOS) return;
            lastMessage.put(player.getUUID(), now);
            String message = query.queryValue(location, local, Flags.DENY_MESSAGE);
            if (message == null || message.isEmpty()) return;
            message = WorldGuard.getInstance().getPlatform().getMatcher().replaceMacros(local, message)
                    .replace("%what%", what);
            local.printRaw(com.sk89q.worldguard.commands.CommandUtils.replaceColorMacros(message));
        }
    }
}
