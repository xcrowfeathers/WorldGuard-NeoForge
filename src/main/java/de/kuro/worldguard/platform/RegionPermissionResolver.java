package de.kuro.worldguard.platform;

import com.sk89q.worldedit.neoforge.NeoForgePermissionsProvider;
import com.sk89q.worldedit.neoforge.NeoForgeWorldEdit;
import net.minecraft.server.level.ServerPlayer;

public interface RegionPermissionResolver {
    boolean hasPermission(ServerPlayer player, String node);

    default boolean hasGroup(ServerPlayer player, String group) {
        for (String candidate : getGroups(player)) {
            if (candidate.equalsIgnoreCase(group)) return true;
        }
        return false;
    }

    default String[] getGroups(ServerPlayer player) {
        return new String[0];
    }

    /** Capture WorldEdit's replaceable provider once at load/reload, not on every action. */
    static RegionPermissionResolver defaultResolver(NeoForgeWorldGuardPlatform platform) {
        NeoForgePermissionsProvider provider = NeoForgeWorldEdit.inst.getPermissionsProvider();
        return (player, node) -> {
            if (!(provider instanceof NeoForgePermissionsProvider.VanillaPermissionsProvider)) {
                return provider.hasPermission(player, node);
            }
            return platform.getGlobalStateManager().get(platform.adaptWorld(player.serverLevel())).opPermissions
                    && player.server.getPlayerList().isOp(player.getGameProfile());
        };
    }
}
