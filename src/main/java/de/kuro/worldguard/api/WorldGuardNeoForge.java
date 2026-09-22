package de.kuro.worldguard.api;

import com.sk89q.worldedit.world.World;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import de.kuro.worldguard.platform.NeoForgeWorldGuardPlatform;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Public server-thread adapter boundary. Core region state remains owned by WorldGuard. */
public final class WorldGuardNeoForge {
    private WorldGuardNeoForge() {}
    public static boolean isReady() {
        return WorldGuard.getInstance().getPlatform() instanceof NeoForgeWorldGuardPlatform platform
                && platform.getRegionContainer() != null;
    }
    public static World world(ServerLevel level) { return platform().adaptWorld(level); }
    public static LocalPlayer player(ServerPlayer player) { return platform().adapt(player); }
    private static NeoForgeWorldGuardPlatform platform() {
        if (!isReady()) throw new IllegalStateException("WorldGuard is not ready");
        return (NeoForgeWorldGuardPlatform) WorldGuard.getInstance().getPlatform();
    }
}