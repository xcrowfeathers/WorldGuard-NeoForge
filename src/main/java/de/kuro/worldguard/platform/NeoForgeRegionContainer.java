package de.kuro.worldguard.platform;

import com.sk89q.worldedit.neoforge.NeoForgeAdapter;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.config.ConfigurationManager;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.managers.migration.MigrationException;
import com.sk89q.worldguard.protection.managers.migration.Migration;
import com.sk89q.worldguard.protection.managers.migration.UUIDMigration;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.Timer;
import java.util.logging.Level;

/** Uses Core's RegionContainerImpl, keyed by Minecraft dimension identity. */
final class NeoForgeRegionContainer extends RegionContainer {
    private final CurrentRegionQueryCache currentCache = new CurrentRegionQueryCache();

    @Override
    public RegionQuery createQuery() {
        return new RegionQuery(currentCache);
    }



    @Override
    protected void autoMigrate() {
        ConfigurationManager config = WorldGuard.getInstance().getPlatform().getGlobalStateManager();
        if (!config.migrateRegionsToUuid) return;

        UUIDMigration migrator = new UUIDMigration(getDriver(), WorldGuard.getInstance().getProfileService(),
                WorldGuard.getInstance().getFlagRegistry());
        migrator.setKeepUnresolvedNames(config.keepUnresolvedNames);
        try {
            migrate(migrator);
            WorldGuard.logger.info("Regions saved after UUID migration! This won't happen again unless "
                    + "you change the relevant configuration option in WorldGuard's config.");
            config.disableUuidMigration();
        } catch (MigrationException error) {
            WorldGuard.logger.log(Level.WARNING, "Failed to execute the migration", error);
        }
    }

    @Override
    public void migrate(Migration migration) throws MigrationException {
        try {
            super.migrate(migration);
        } finally {
            if (migration instanceof UUIDMigration uuidMigration) {
                // Core 7.0.12 cancels its progress task but leaves this
                // non-daemon timer alive after auto or command migration.
                try {
                    Field timerField = UUIDMigration.class.getDeclaredField("timer");
                    timerField.setAccessible(true);
                    ((Timer) timerField.get(uuidMigration)).cancel();
                } catch (ReflectiveOperationException | SecurityException error) {
                    WorldGuard.logger.log(Level.WARNING, "Could not stop WorldGuard's UUID migration timer", error);
                }
            }
        }
    }


    private static String storageId(World world) {
        return storageId(NeoForgeAdapter.adapt(world));
    }

    static String storageId(ServerLevel level) {
        // '~' is not legal in ResourceLocation, so these substitutions are
        // reversible and safe for paths and Java thread-name format strings.
        return level.dimension().location().toString().replace(":", "~").replace("/", "~s");
    }

    @Override
    @Nullable
    public RegionManager get(World world) {
        return container.get(storageId(world));
    }

    @Override
    @Nullable
    protected RegionManager load(World world) {
        if (!WorldGuard.getInstance().getPlatform().getGlobalStateManager().get(world).useRegions) {
            return null;
        }
        return container.load(storageId(world));
    }

    void loadWorld(World world) {
        synchronized (lock) {
            load(world);
        }
    }

    void loadChunk(ServerLevel level, int x, int z) {
        RegionManager manager = container.get(storageId(level));
        if (manager != null) {
            manager.loadChunk(BlockVector2.at(x, z));
        }
    }

    void unloadChunk(ServerLevel level, int x, int z) {
        RegionManager manager = container.get(storageId(level));
        if (manager != null) {
            manager.unloadChunk(BlockVector2.at(x, z));
        }
    }

    @Override
    public void unload(World world) {
        synchronized (lock) {
            container.unload(storageId(world));
            cache.invalidateAll();
            currentCache.invalidateAll();
        }
    }

    void shutdown() {
        synchronized (lock) {
            if (container != null) {
                container.shutdown();
                container = null;
            }
            cache.invalidateAll();
            currentCache.invalidateAll();
        }
    }
}
