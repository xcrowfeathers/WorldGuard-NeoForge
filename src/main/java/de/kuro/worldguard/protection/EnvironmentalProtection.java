package de.kuro.worldguard.protection;

import com.sk89q.worldedit.util.Location;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldguard.config.WorldConfiguration;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.protection.association.DelayedRegionOverlapAssociation;
import com.sk89q.worldguard.protection.association.RegionAssociable;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import de.kuro.worldguard.platform.NeoForgeWorldGuardPlatform;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class EnvironmentalProtection {
    private final NeoForgeWorldGuardPlatform platform;

    public EnvironmentalProtection(NeoForgeWorldGuardPlatform platform) {
        this.platform = platform;
    }

    public boolean active() { return platform.getRegionContainer() != null; }

    public LocalPlayer player(ServerPlayer player) { return platform.adapt(player); }

    public WorldConfiguration config(ServerLevel level) {
        return platform.getGlobalStateManager().get(platform.adaptWorld(level));
    }

    public Context context(ServerLevel level) {
        World world = platform.adaptWorld(level);
        return new Context(world, platform.getRegionContainer().createQuery(), config(level));
    }

    public static final class Context {
        private final World world;
        private final RegionQuery query;
        private final WorldConfiguration config;

        private Context(World world, RegionQuery query, WorldConfiguration config) {
            this.world = world;
            this.query = query;
            this.config = config;
        }

        public WorldConfiguration config() { return config; }
        public RegionQuery query() { return query; }

        public Location location(BlockPos pos) {
            return new Location(world, pos.getX(), pos.getY(), pos.getZ());
        }

        public boolean state(BlockPos pos, StateFlag flag) {
            return !config.useRegions || query.testState(location(pos), (RegionAssociable) null, flag);
        }

        public RegionAssociable source(BlockPos pos) {
            return new DelayedRegionOverlapAssociation(query, location(pos), config.useMaxPriorityAssociation);
        }

        public boolean build(BlockPos pos, RegionAssociable source, StateFlag... flags) {
            return !config.useRegions || query.testBuild(location(pos), source, flags);
        }
    }
}
