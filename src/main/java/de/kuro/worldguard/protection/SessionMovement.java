package de.kuro.worldguard.protection;

import com.sk89q.worldedit.neoforge.NeoForgeAdapter;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.commands.CommandUtils;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import com.sk89q.worldguard.session.MoveType;
import de.kuro.worldguard.platform.NeoForgeWorldGuardPlatform;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class SessionMovement {
    private static SessionMovement installed;
    private final NeoForgeWorldGuardPlatform platform;
    private final Map<UUID, BlockLocation> observed = new HashMap<>();
    private boolean correcting;

    private record BlockLocation(ServerLevel level, int x, int y, int z) {
        static BlockLocation of(ServerPlayer player) {
            return new BlockLocation(player.serverLevel(), player.getBlockX(), player.getBlockY(), player.getBlockZ());
        }
    }

    public SessionMovement(NeoForgeWorldGuardPlatform platform) { this.platform = platform; }

    public void install() { installed = this; }
    public void clear() { observed.clear(); correcting = false; }
    public static SessionMovement installed() { return installed; }

    public boolean active() { return platform.getRegionContainer() != null; }

    public void observe(ServerPlayer player) { observed.put(player.getUUID(), BlockLocation.of(player)); }
    public void forget(ServerPlayer player) { observed.remove(player.getUUID()); }

    public Location packetMove(ServerPlayer player, double x, double y, double z, float yaw, float pitch) {
        if (!active() || correcting || !player.server.isSameThread()
                || !platform.getGlobalStateManager().usePlayerMove
                || !platform.getGlobalStateManager().get(platform.adaptWorld(player.serverLevel())).useRegions
                || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || Mth.floor(x) == player.getBlockX() && Mth.floor(y) == player.getBlockY()
                && Mth.floor(z) == player.getBlockZ()) return null;
        MoveType type = player.isFallFlying() ? MoveType.GLIDE
                : player.isSwimming() ? MoveType.SWIM : player.isPassenger() ? MoveType.RIDE : MoveType.MOVE;
        LocalPlayer local = platform.adapt(player);
        return platform.getSessionManager().get(local).testMoveTo(local,
                new Location(platform.adaptWorld(player.serverLevel()), x, y, z, yaw, pitch), type);
    }

    public void observedTick(ServerPlayer player) {
        if (!active()) return;
        BlockLocation previous = observed.get(player.getUUID());
        if (previous != null && previous.level() == player.serverLevel()
                && previous.x() == player.getBlockX() && previous.y() == player.getBlockY()
                && previous.z() == player.getBlockZ()) return;
        observed.put(player.getUUID(), BlockLocation.of(player));
        if (previous == null || correcting
                || !platform.getGlobalStateManager().usePlayerMove
                || !platform.getGlobalStateManager().get(platform.adaptWorld(player.serverLevel())).useRegions) return;
        LocalPlayer local = platform.adapt(player);
        MoveType type = player.isPassenger() ? MoveType.RIDE : MoveType.TELEPORT;
        Location denied = platform.getSessionManager().get(local).testMoveTo(local, local.getLocation(), type);
        if (denied != null) correct(player, denied);
    }

    public boolean teleport(EntityTeleportEvent event) {
        if (!active() || correcting || event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)
                || !platform.getGlobalStateManager().usePlayerTeleports) return true;
        LocalPlayer local = platform.adapt(player);
        World world = platform.adaptWorld(player.serverLevel());
        if (!platform.getGlobalStateManager().get(world).useRegions) return true;
        Location from = local.getLocation();
        Location to = new Location(world, event.getTargetX(), event.getTargetY(), event.getTargetZ(),
                player.getYRot(), player.getXRot());
        if (!platform.getSessionManager().hasBypass(local, world)) {
            RegionQuery query = platform.getRegionContainer().createQuery();
            if (event instanceof EntityTeleportEvent.EnderPearl) {
                if (!teleportFlag(local, query, from, to, Flags.ENDERPEARL)) return false;
            } else if (event instanceof EntityTeleportEvent.ChorusFruit) {
                if (!teleportFlag(local, query, from, to, Flags.CHORUS_TELEPORT)) return false;
            }
        }
        return platform.getSessionManager().get(local).testMoveTo(local, to, MoveType.TELEPORT) == null;
    }

    private static boolean teleportFlag(LocalPlayer player, RegionQuery query, Location from,
                                        Location to, com.sk89q.worldguard.protection.flags.StateFlag flag) {
        ApplicableRegionSet source = query.getApplicableRegions(from);
        if (!source.testState(player, flag)) {
            message(player, source.queryValue(player, Flags.EXIT_DENY_MESSAGE));
            return false;
        }
        ApplicableRegionSet destination = query.getApplicableRegions(to);
        if (!destination.testState(player, flag)) {
            message(player, destination.queryValue(player, Flags.ENTRY_DENY_MESSAGE));
            return false;
        }
        return true;
    }

    private static void message(LocalPlayer player, String message) {
        if (message != null && !message.isEmpty()) player.printRaw(CommandUtils.replaceColorMacros(message));
    }

    public boolean dimensionTeleport(ServerPlayer player, ServerLevel target, double x, double y, double z,
                                     float yaw, float pitch) {
        if (!active() || correcting || !platform.getGlobalStateManager().usePlayerTeleports) return true;
        LocalPlayer local = platform.adapt(player);
        Location to = new Location(platform.adaptWorld(target), x, y, z, yaw, pitch);
        return platform.getSessionManager().get(local).testMoveTo(local, to, MoveType.TELEPORT) == null;
    }

    public void afterNonCancellableMove(ServerPlayer player, MoveType type) {
        if (!active()) return;
        LocalPlayer local = platform.adapt(player);
        platform.getSessionManager().get(local).testMoveTo(local, local.getLocation(), type, true);
        observe(player);
    }

    private void correct(ServerPlayer player, Location previous) {
        correcting = true;
        try {
            ServerLevel level = NeoForgeAdapter.adapt((World) previous.getExtent());
            if (player.serverLevel() == level) {
                player.connection.teleport(previous.getX(), previous.getY(), previous.getZ(),
                        previous.getYaw(), previous.getPitch());
            } else {
                player.teleportTo(level, previous.getX(), previous.getY(), previous.getZ(),
                        previous.getYaw(), previous.getPitch());
            }
            observe(player);
        } finally {
            correcting = false;
        }
    }
}
