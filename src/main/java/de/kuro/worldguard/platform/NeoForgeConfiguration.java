package de.kuro.worldguard.platform;

import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.neoforge.NeoForgeAdapter;
import com.sk89q.worldguard.config.ConfigurationManager;
import com.sk89q.worldguard.config.WorldConfiguration;
import com.sk89q.worldguard.protection.managers.storage.file.DirectoryYamlDriver;
import com.sk89q.worldguard.blacklist.Blacklist;
import com.sk89q.worldguard.blacklist.logger.ConsoleHandler;
import com.sk89q.worldguard.blacklist.logger.DatabaseHandler;
import com.sk89q.worldguard.blacklist.logger.FileHandler;
import com.sk89q.worldedit.world.entity.EntityTypes;

import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.logging.Level;

final class NeoForgeConfiguration extends ConfigurationManager {
    private final File dataFolder;
    private final Map<String, PortWorldConfiguration> worlds = new ConcurrentHashMap<>();
    private WorldConfiguration defaultConfiguration;
    private Map<?, ?> root = Map.of();
    private volatile boolean hasBlacklistRules;

    NeoForgeConfiguration(Path dataFolder) {
        this.dataFolder = dataFolder.toFile();
    }

    @Override
    public File getDataFolder() {
        return dataFolder;
    }

    @Override
    public void load() {
        if (!dataFolder.isDirectory() && !dataFolder.mkdirs()) {
            throw new IllegalStateException("Cannot create WorldGuard data folder: " + dataFolder);
        }
        Map<?, ?> nextRoot = readConfiguration(dataFolder.toPath().resolve("config.yml"));
        PortWorldConfiguration nextDefault = new PortWorldConfiguration("default", nextRoot, Map.of());
        Map<String, PortWorldConfiguration> nextWorlds = new HashMap<>();
        boolean nextMigrate;
        boolean nextKeepNames;
        boolean nextSpawnEvent;
        boolean nextGodPermission;
        boolean nextGodGroup;
        boolean nextAmphibiousGroup;
        boolean nextPlayerMove;
        boolean nextPlayerTeleports;
        boolean nextParticleEffects;
        boolean nextDisableBypass;
        boolean nextAnnounceBypass;
        boolean nextDisablePermissionCache;
        try {
            Path worldsDirectory = dataFolder.toPath().resolve("worlds");
            if (Files.isDirectory(worldsDirectory)) {
                try (var directories = Files.list(worldsDirectory)) {
                    directories.filter(Files::isDirectory).forEach(directory -> {
                        Path config = directory.resolve("config.yml");
                        if (Files.isRegularFile(config) || Files.isRegularFile(directory.resolve("blacklist.txt"))) {
                            String id = directory.getFileName().toString();
                            try {
                                nextWorlds.put(id, new PortWorldConfiguration(id, nextRoot, readConfiguration(config)));
                            } catch (RuntimeException error) {
                                Logger.getLogger(NeoForgeConfiguration.class.getName()).log(Level.WARNING,
                                        "Invalid WorldGuard configuration for dimension " + id
                                                + "; using global settings without altering its files", error);
                                nextWorlds.put(id, new PortWorldConfiguration(id, nextRoot, Map.of(), false));
                            }
                        }
                    });
                } catch (IOException error) {
                    throw new IllegalStateException("Cannot read WorldGuard dimension configurations", error);
                }
            }
            nextMigrate = flag(nextRoot, "regions.uuid-migration.perform-on-next-start", true);
            nextKeepNames = flag(nextRoot, "regions.uuid-migration.keep-names-that-lack-uuids", true);
            nextSpawnEvent = flag(nextRoot, "regions.use-creature-spawn-event", true);
            nextGodPermission = flag(nextRoot, "auto-invincible", false);
            nextGodGroup = flag(nextRoot, "auto-invincible-group", false);
            nextAmphibiousGroup = flag(nextRoot, "auto-no-drowning-group", false);
            nextPlayerMove = flag(nextRoot, "use-player-move-event", true);
            nextPlayerTeleports = flag(nextRoot, "use-player-teleports", true);
            nextParticleEffects = flag(nextRoot, "use-particle-effects", true);
            nextDisableBypass = flag(nextRoot, "regions.disable-bypass-by-default", false);
            nextAnnounceBypass = flag(nextRoot, "regions.announce-bypass-status", false);
            nextDisablePermissionCache = flag(nextRoot, "disable-permission-cache", false);
        } catch (RuntimeException error) {
            closeBlacklist(nextDefault);
            nextWorlds.values().forEach(NeoForgeConfiguration::closeBlacklist);
            throw error;
        }
        selectedRegionStoreDriver = new DirectoryYamlDriver(getWorldsDataFolder(), "regions.yml");
        migrateRegionsToUuid = nextMigrate;
        keepUnresolvedNames = nextKeepNames;
        useRegionsCreatureSpawnEvent = nextSpawnEvent;
        useGodPermission = nextGodPermission;
        useGodGroup = nextGodGroup;
        useAmphibiousGroup = nextAmphibiousGroup;
        usePlayerMove = nextPlayerMove;
        usePlayerTeleports = nextPlayerTeleports;
        particleEffects = nextParticleEffects;
        disableDefaultBypass = nextDisableBypass;
        announceBypassStatus = nextAnnounceBypass;
        disablePermissionCache = nextDisablePermissionCache;
        closeBlacklist(defaultConfiguration);
        worlds.values().forEach(NeoForgeConfiguration::closeBlacklist);
        root = nextRoot;
        defaultConfiguration = nextDefault;
        worlds.clear();
        worlds.putAll(nextWorlds);
        hasBlacklistRules = nextDefault.getBlacklist() != null
                || nextWorlds.values().stream().anyMatch(world -> world.getBlacklist() != null);
    }

    @Override
    public void unload() {
        // Core calls unload() immediately before load() during /wg reload.
        // Retain the last valid snapshot if parsing the replacement fails.
    }

    void close() {
        closeBlacklist(defaultConfiguration);
        worlds.values().forEach(NeoForgeConfiguration::closeBlacklist);
        worlds.clear();
        hasBlacklistRules = false;
    }

    @Override
    public WorldConfiguration get(World world) {
        if (world == null) return defaultConfiguration;
        String id = NeoForgeRegionContainer.storageId(NeoForgeAdapter.adapt(world));
        return getForDimensionId(id);
    }

    WorldConfiguration getForDimensionId(String id) {
        return worlds.computeIfAbsent(id, key -> {
            Path folder = dataFolder.toPath().resolve("worlds").resolve(key);
            try {
                PortWorldConfiguration loaded = new PortWorldConfiguration(key, root,
                        readConfiguration(folder.resolve("config.yml")));
                if (loaded.getBlacklist() != null) hasBlacklistRules = true;
                return loaded;
            } catch (RuntimeException error) {
                Logger.getLogger(NeoForgeConfiguration.class.getName()).log(Level.WARNING,
                        "Invalid WorldGuard configuration for dimension " + key
                                + "; using global settings without altering its files", error);
                PortWorldConfiguration fallback = new PortWorldConfiguration(key, root, Map.of(), false);
                if (fallback.getBlacklist() != null) hasBlacklistRules = true;
                return fallback;
            }
        });
    }

    boolean hasBlacklistRules() { return hasBlacklistRules; }

    void unloadDimension(String id) {
        closeBlacklist(worlds.remove(id));
        hasBlacklistRules = defaultConfiguration != null && defaultConfiguration.getBlacklist() != null
                || worlds.values().stream().anyMatch(world -> world.getBlacklist() != null);
    }

    Set<String> blockedPotionEffects(String id) {
        return ((PortWorldConfiguration) getForDimensionId(id)).blockedPotionEffects;
    }

    boolean blockPotionsAlways(String id) {
        return ((PortWorldConfiguration) getForDimensionId(id)).blockPotionsAlways;
    }

    @Override
    public void disableUuidMigration() {
        migrateRegionsToUuid = false;
        Path file = dataFolder.toPath().resolve("config.yml");
        Map<String, Object> document = new LinkedHashMap<>();
        root.forEach((key, entry) -> document.put(String.valueOf(key), entry));
        Map<String, Object> regions = mutableMapping(document.get("regions"));
        Map<String, Object> migration = mutableMapping(regions.get("uuid-migration"));
        migration.put("perform-on-next-start", false);
        regions.put("uuid-migration", migration);
        document.put("regions", regions);
        Path temporary = null;
        try {
            temporary = Files.createTempFile(file.getParent(), "config-", ".yml");
            Files.writeString(temporary, new Yaml().dump(document));
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException error) {
            throw new IllegalStateException("Cannot record completed UUID migration", error);
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
            }
        }
        root = document;
    }

    private final class PortWorldConfiguration extends WorldConfiguration {
        private final String dimensionId;
        private final Map<?, ?> global;
        private final Map<?, ?> overrides;
        private final boolean useDimensionBlacklist;
        private Set<String> blockedPotionEffects = Set.of();

        PortWorldConfiguration(String dimensionId, Map<?, ?> global, Map<?, ?> overrides) {
            this(dimensionId, global, overrides, true);
        }

        PortWorldConfiguration(String dimensionId, Map<?, ?> global, Map<?, ?> overrides,
                               boolean useDimensionBlacklist) {
            this.dimensionId = dimensionId;
            this.global = global;
            this.overrides = overrides;
            this.useDimensionBlacklist = useDimensionBlacklist;
            loadConfiguration();
        }

        private boolean option(String key, boolean fallback) {
            return flag(overrides, key, flag(global, key, fallback));
        }

        private int number(String key, int fallback) {
            return integer(overrides, key, integer(global, key, fallback));
        }

        private String word(String key, String fallback) {
            return string(overrides, key, string(global, key, fallback));
        }

        private Set<String> list(String key) {
            return value(overrides, key) == null ? strings(global, key) : strings(overrides, key);
        }

        @Override
        public void loadConfiguration() {
            opPermissions = option("op-permissions", true);
            useRegions = option("regions.enable", true);
            highFreqFlags = option("regions.high-frequency-flags", false);
            checkLiquidFlow = option("regions.protect-against-liquid-flow", false);
            explosionFlagCancellation = option("regions.explosion-flags-block-entity-damage", true);
            useMaxPriorityAssociation = option("protection.use-max-priority-association", false);
            boundedLocationFlags = option("regions.location-flags-only-inside-regions", false);
            forceDefaultTitleTimes = option("regions.titles-always-use-default-times", true);
            regionCancelEmptyChatEvents = option("regions.cancel-chat-without-recipients", true);
            regionNetherPortalProtection = option("regions.nether-portal-protection", true);
            regionInvinciblityRemovesMobs = option("regions.invincibility-removes-mobs", false);
            maxRegionCountPerPlayer = number("regions.max-region-count-per-player.default", 7);
            maxRegionCounts = regionCounts();
            maxClaimVolume = number("regions.max-claim-volume", 30000);
            claimOnlyInsideExistingRegions = option("regions.claim-only-inside-existing-regions", false);
            setParentOnClaim = word("regions.set-parent-on-claim", "");
            regionWand = word("regions.wand", "minecraft:leather");
            summaryOnStart = option("summary-on-start", true);
            itemDurability = option("protection.item-durability", true);
            blockedPotionEffects = list("gameplay.block-potions").stream()
                    .map(value -> value.toLowerCase(Locale.ROOT))
                    .map(value -> value.contains(":") ? value : "minecraft:" + value)
                    .collect(Collectors.toUnmodifiableSet());
            blockPotionsAlways = option("gameplay.block-potions-overly-reliably", false);
            disableHealthRegain = option("default.disable-health-regain", false);
            disablePlayerCropTrampling = option("crops.disable-player-trampling", false);
            disableCreatureCropTrampling = option("crops.disable-creature-trampling", false);
            disablePlayerTurtleEggTrampling = option("turtle-egg.disable-player-trampling", false);
            disableCreatureTurtleEggTrampling = option("turtle-egg.disable-creature-trampling", false);
            disablePlayerSnifferEggTrampling = option("sniffer-egg.disable-player-trampling", false);
            disableCreatureSnifferEggTrampling = option("sniffer-egg.disable-creature-trampling", false);
            disableExpDrops = option("protection.disable-xp-orb-drops", false);
            buildPermissions = option("build-permission-nodes.enable", false);
            buildPermissionDenyMessage = word("build-permission-nodes.deny-message",
                    "&eSorry, but you are not permitted to do that here.");
            strictEntitySpawn = option("event-handling.block-entity-spawns-with-untraceable-cause", false);
            ignoreHopperMoveEvents = option("event-handling.ignore-hopper-item-move-events", false);
            breakDeniedHoppers = option("event-handling.break-hoppers-on-denied-move", true);

            blockTNTExplosions = option("ignition.block-tnt", false);
            blockTNTBlockDamage = option("ignition.block-tnt-block-damage", false);
            blockLighter = option("ignition.block-lighter", false);
            preventLavaFire = option("fire.disable-lava-fire-spread", false);
            disableFireSpread = option("fire.disable-all-fire-spread", false);
            disableFireSpreadBlocks = list("fire.disable-fire-spread-blocks");
            allowedLavaSpreadOver = list("fire.lava-spread-blocks");
            preventWaterDamage = list("physics.disable-water-damage-blocks");

            blockCreeperExplosions = option("mobs.block-creeper-explosions", false);
            blockCreeperBlockDamage = option("mobs.block-creeper-block-damage", false);
            blockWitherExplosions = option("mobs.block-wither-explosions", false);
            blockWitherBlockDamage = option("mobs.block-wither-block-damage", false);
            blockWitherSkullExplosions = option("mobs.block-wither-skull-explosions", false);
            blockWitherSkullBlockDamage = option("mobs.block-wither-skull-block-damage", false);
            blockFireballExplosions = option("mobs.block-fireball-explosions", false);
            blockFireballBlockDamage = option("mobs.block-fireball-block-damage", false);
            blockEnderDragonBlockDamage = option("mobs.block-enderdragon-block-damage", false);
            blockEnderDragonPortalCreation = option("mobs.block-enderdragon-portal-creation", false);
            blockWindChargeExplosions = option("mobs.block-windcharge-explosions", false);
            blockOtherExplosions = option("mobs.block-other-explosions", false);
            disableEndermanGriefing = option("mobs.disable-enderman-griefing", false);
            disableSnowmanTrails = option("mobs.disable-snowman-trails", false);
            blockEntityPaintingDestroy = option("mobs.block-painting-destroy", false);
            blockEntityItemFrameDestroy = option("mobs.block-item-frame-destroy", false);
            blockEntityArmorStandDestroy = option("mobs.block-armor-stand-destroy", false);
            blockEntityVehicleEntry = option("mobs.block-vehicle-entry", false);
            blockPluginSpawning = option("mobs.block-plugin-spawning", true);
            blockGroundSlimes = option("mobs.block-above-ground-slimes", false);
            blockZombieDoorDestruction = option("mobs.block-zombie-door-destruction", false);
            allowTamedSpawns = option("mobs.allow-tamed-spawns", true);
            blockCreatureSpawn = new HashSet<>();
            for (String name : list("mobs.block-creature-spawn")) {
                String key = name.toLowerCase(Locale.ROOT);
                var type = EntityTypes.get(key.contains(":") ? key : "minecraft:" + key);
                if (type != null) blockCreatureSpawn.add(type);
            }

            disableMobDamage = option("player-damage.disable-mob-damage", false);
            disableFallDamage = option("player-damage.disable-fall-damage", false);
            disableFireDamage = option("player-damage.disable-fire-damage", false);
            disableLavaDamage = option("player-damage.disable-lava-damage", false);
            disableLightningDamage = option("player-damage.disable-lightning-damage", false);
            disableDrowningDamage = option("player-damage.disable-drowning-damage", false);
            disableSuffocationDamage = option("player-damage.disable-suffocation-damage", false);
            disableVoidDamage = option("player-damage.disable-void-damage", false);
            disableExplosionDamage = option("player-damage.disable-explosion-damage", false);
            disableContactDamage = option("player-damage.disable-contact-damage", false);
            disableDeathMessages = option("player-damage.disable-death-messages", false);
            disableMushroomSpread = option("dynamics.disable-mushroom-spread", false);
            disableLeafDecay = option("dynamics.disable-leaf-decay", false);
            disableGrassGrowth = option("dynamics.disable-grass-growth", false);
            disableMyceliumSpread = option("dynamics.disable-mycelium-spread", false);
            disableVineGrowth = option("dynamics.disable-vine-growth", false);
            disableRockGrowth = option("dynamics.disable-rock-growth", false);
            disableSculkGrowth = option("dynamics.disable-sculk-growth", false);
            disableCropGrowth = option("dynamics.disable-crop-growth", false);
            disableSoilDehydration = option("dynamics.disable-soil-dehydration", false);
            disableSoilMoistureChange = option("dynamics.disable-soil-moisture-change", false);
            disableCoralBlockFade = option("dynamics.disable-coral-block-fade", false);
            disableCopperBlockFade = option("dynamics.disable-copper-block-fade", false);
            disableSnowFormation = option("dynamics.disable-snow-formation", false);
            disableSnowMelting = option("dynamics.disable-snow-melting", false);
            disableIceFormation = option("dynamics.disable-ice-formation", false);
            disableIceMelting = option("dynamics.disable-ice-melting", false);
            allowedSnowFallOver = list("dynamics.snow-fall-blocks");
            disallowedLightningBlocks = list("weather.prevent-lightning-strike-blocks");
            preventLightningFire = option("weather.disable-lightning-strike-fire", false);
            loadBlacklist();
        }

        private void loadBlacklist() {
            Path base = dataFolder.toPath();
            Path dimensionFile = base.resolve("worlds").resolve(dimensionId).resolve("blacklist.txt");
            Path globalFile = base.resolve("blacklist.txt");
            Path file = useDimensionBlacklist && Files.isRegularFile(dimensionFile)
                    ? dimensionFile : globalFile;
            if (!Files.isRegularFile(file)) {
                blacklist = null;
                return;
            }
            Blacklist loaded = new Blacklist(option("blacklist.use-as-whitelist", false));
            try {
                loaded.load(file.toFile());
            } catch (IOException error) {
                throw new IllegalStateException("Cannot load WorldGuard blacklist " + file, error);
            }
            if (loaded.isEmpty()) {
                blacklist = null;
                return;
            }
            Logger logger = Logger.getLogger("com.sk89q.worldguard.blacklist");
            try {
                if (option("blacklist.logging.console.enable", true)) {
                    loaded.getLogger().addHandler(new ConsoleHandler(dimensionId, logger));
                }
                if (option("blacklist.logging.file.enable", false)) {
                    loaded.getLogger().addHandler(new FileHandler(
                            word("blacklist.logging.file.path", "worldguard/logs/%Y-%m-%d.log"),
                            Math.max(1, number("blacklist.logging.file.open-files", 10)), dimensionId, logger));
                }
                if (option("blacklist.logging.database.enable", false)) {
                    loaded.getLogger().addHandler(new DatabaseHandler(
                            word("blacklist.logging.database.dsn", "jdbc:mysql://localhost:3306/minecraft"),
                            word("blacklist.logging.database.user", "root"),
                            word("blacklist.logging.database.pass", ""),
                            word("blacklist.logging.database.table", "blacklist_events"), dimensionId, logger));
                }
            } catch (RuntimeException error) {
                loaded.getLogger().close();
                throw error;
            }
            blacklist = loaded;
        }

        private Map<String, Integer> regionCounts() {
            Map<String, Integer> counts = new HashMap<>();
            counts.put(null, maxRegionCountPerPlayer);
            Object raw = value(global, "regions.max-region-count-per-player");
            if (raw instanceof Map<?, ?> map) addCounts(counts, map);
            raw = value(overrides, "regions.max-region-count-per-player");
            if (raw instanceof Map<?, ?> map) addCounts(counts, map);
            return counts;
        }
    }

    private static void closeBlacklist(WorldConfiguration config) {
        if (config != null && config.getBlacklist() != null) config.getBlacklist().getLogger().close();
    }

    private static Map<?, ?> readConfiguration(Path file) {
        if (!Files.exists(file)) return Map.of();
        try (InputStream input = Files.newInputStream(file)) {
            Object loaded = new Yaml().load(input);
            if (loaded == null) return Map.of();
            if (loaded instanceof Map<?, ?> map) return map;
            throw new IllegalArgumentException("WorldGuard config.yml must contain a YAML mapping");
        } catch (IOException error) {
            throw new IllegalStateException("Cannot read " + file, error);
        }
    }

    private static Map<String, Object> mutableMapping(Object source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        if (source instanceof Map<?, ?> map) {
            map.forEach((key, value) -> copy.put(String.valueOf(key), value));
        }
        return copy;
    }

    private static void addCounts(Map<String, Integer> target, Map<?, ?> source) {
        source.forEach((key, value) -> {
            if (!"default".equalsIgnoreCase(String.valueOf(key))) {
                if (!(value instanceof Number number)) {
                    throw new IllegalArgumentException("WorldGuard region count for " + key + " must be an integer");
                }
                target.put(String.valueOf(key), number.intValue());
            }
        });
    }

    private static Object value(Map<?, ?> root, String key) {
        Object current = root;
        for (String part : key.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) return null;
            current = map.get(part);
        }
        return current;
    }

    private static boolean flag(Map<?, ?> root, String key, boolean fallback) {
        Object value = value(root, key);
        if (value == null) return fallback;
        if (value instanceof Boolean booleanValue) return booleanValue;
        throw new IllegalArgumentException("WorldGuard config " + key + " must be true or false");
    }

    private static int integer(Map<?, ?> root, String key, int fallback) {
        Object value = value(root, key);
        if (value == null) return fallback;
        if (value instanceof Number number && number.doubleValue() == number.intValue()) return number.intValue();
        throw new IllegalArgumentException("WorldGuard config " + key + " must be an integer");
    }

    private static String string(Map<?, ?> root, String key, String fallback) {
        Object value = value(root, key);
        if (value == null) return fallback;
        if (value instanceof String text) return text;
        throw new IllegalArgumentException("WorldGuard config " + key + " must be a string");
    }

    private static Set<String> strings(Map<?, ?> root, String key) {
        Object value = value(root, key);
        if (value == null) return Set.of();
        if (value instanceof java.util.List<?> list) {
            java.util.HashSet<String> result = new java.util.HashSet<>();
            for (Object item : list) {
                if (!(item instanceof String string)) {
                    throw new IllegalArgumentException("WorldGuard config " + key + " must be a string list");
                }
                result.add(string);
            }
            return result;
        }
        throw new IllegalArgumentException("WorldGuard config " + key + " must be a list");
    }
}
