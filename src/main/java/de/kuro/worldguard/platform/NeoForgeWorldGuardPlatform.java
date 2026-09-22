package de.kuro.worldguard.platform;

import com.sk89q.worldedit.neoforge.NeoForgeAdapter;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.util.report.ReportList;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.gamemode.GameMode;
import com.sk89q.worldedit.world.gamemode.GameModes;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.config.ConfigurationManager;
import com.sk89q.worldguard.internal.platform.DebugHandler;
import com.sk89q.worldguard.internal.platform.StringMatcher;
import com.sk89q.worldguard.internal.platform.WorldGuardPlatform;
import com.sk89q.worldguard.protection.flags.FlagContext;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.session.SessionManager;
import com.sk89q.worldguard.util.profile.cache.ProfileCache;
import com.sk89q.worldguard.util.profile.resolver.CacheForwardingService;
import com.sk89q.worldguard.util.profile.resolver.HttpRepositoryService;
import com.sk89q.worldguard.util.profile.resolver.ProfileService;
import com.sk89q.worldguard.util.profile.Profile;
import de.kuro.worldguard.api.FlagContextCreateEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Per-server state behind WorldGuard Core's singleton platform contract. */
public final class NeoForgeWorldGuardPlatform implements WorldGuardPlatform {
    private MinecraftServer server;
    private Path configDir;
    private NeoForgeConfiguration configuration;
    private NeoForgeRegionContainer regions;
    private NeoForgeSessionManager sessions;
    private NeoForgeStringMatcher matcher;
    private KnownProfileService knownProfiles;
    private RegionPermissionResolver permissionResolver;
    private boolean customPermissionResolver;
    private final Map<UUID, NeoForgeLocalPlayer> players = new HashMap<>();
    private final Map<ServerLevel, World> worlds = new IdentityHashMap<>();
    private boolean sessionRefreshPending;

    public void bind(MinecraftServer server) {
        if (this.server != null) {
            throw new IllegalStateException("WorldGuard is already bound to a server");
        }
        this.server = server;
        this.configDir = server.getWorldPath(LevelResource.ROOT).resolve("worldguard");
    }

    public void unbind() {
        players.clear();
        worlds.clear();
        sessionRefreshPending = false;
        server = null;
        configDir = null;
        knownProfiles = null;
    }

    MinecraftServer server() {
        if (server == null) {
            throw new IllegalStateException("WorldGuard server is not active");
        }
        return server;
    }

    public NeoForgeLocalPlayer adapt(ServerPlayer player) {
        NeoForgeLocalPlayer current = players.get(player.getUUID());
        if (current == null || current.getHandle() != player) {
            current = new NeoForgeLocalPlayer(player, this);
            players.put(player.getUUID(), current);
        }
        return current;
    }

    /** Reuse the WorldEdit world wrapper on frequent player protection checks. */
    public World adaptWorld(ServerLevel level) {
        return worlds.computeIfAbsent(level, NeoForgeAdapter::adapt);
    }

    public java.util.Set<String> blockedPotionEffects(ServerLevel level) {
        return configuration.blockedPotionEffects(NeoForgeRegionContainer.storageId(level));
    }

    public boolean blockPotionsAlways(ServerLevel level) {
        return configuration.blockPotionsAlways(NeoForgeRegionContainer.storageId(level));
    }

    public boolean hasBlacklistRules() {
        return configuration != null && configuration.hasBlacklistRules();
    }

    public void removePlayer(ServerPlayer player) {
        NeoForgeLocalPlayer adapted = players.remove(player.getUUID());
        if (adapted != null && sessions != null) {
            sessions.remove(adapted);
        }
    }

    public void rememberProfile(ServerPlayer player) {
        Profile profile = new Profile(player.getUUID(), player.getGameProfile().getName());
        if (knownProfiles != null) knownProfiles.remember(profile);
        ProfileCache cache = WorldGuard.getInstance().getProfileCache();
        if (cache != null) cache.put(profile);
    }



    /** Called before a Core command which may mutate regions asynchronously. */
    public void regionCommandStarted() {
        sessionRefreshPending = true;
    }

    /** One server task drives Core's timed handlers and player-only view packets. */
    public void tickSessions() {
        if (sessions == null) return;
        if (sessionRefreshPending && WorldGuard.getInstance().getSupervisor().getTasks().isEmpty()) {
            sessionRefreshPending = false;
            sessions.resetAllStates();
        }
        for (ServerPlayer player : server().getPlayerList().getPlayers()) {
            NeoForgeLocalPlayer local = adapt(player);
            sessions.tickTimed(local);
            local.syncView();
        }
    }

    public void setPermissionResolver(RegionPermissionResolver resolver) {
        this.permissionResolver = java.util.Objects.requireNonNull(resolver);
        customPermissionResolver = true;
    }

    public void refreshPermissionResolver() {
        if (!customPermissionResolver) permissionResolver = RegionPermissionResolver.defaultResolver(this);
    }

    RegionPermissionResolver permissionResolver() {
        return permissionResolver;
    }

    Collection<LocalPlayer> onlinePlayers() {
        List<LocalPlayer> result = new ArrayList<>();
        for (ServerPlayer player : server().getPlayerList().getPlayers()) {
            result.add(adapt(player));
        }
        return result;
    }

    public void loadLevel(World world) {
        if (configuration != null) configuration.get(world);
        if (regions != null) {
            regions.loadWorld(world);
        }
    }

    public void unloadLevel(World world) {
        if (regions != null) {
            regions.unload(world);
        }
        if (configuration != null) {
            configuration.unloadDimension(NeoForgeRegionContainer.storageId(NeoForgeAdapter.adapt(world)));
        }
        worlds.remove(NeoForgeAdapter.adapt(world));
    }

    public void loadChunk(ServerLevel level, int x, int z) {
        if (regions != null) {
            regions.loadChunk(level, x, z);
        }
    }

    public void unloadChunk(ServerLevel level, int x, int z) {
        if (regions != null) {
            regions.unloadChunk(level, x, z);
        }
    }

    @Override
    public String getPlatformName() {
        return "NeoForge";
    }

    @Override
    public String getPlatformVersion() {
        return ModList.get().getModContainerById("neoforge")
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("unknown");
    }

    @Override
    public void notifyFlagContextCreate(FlagContext.FlagContextBuilder builder) {
        NeoForge.EVENT_BUS.post(new FlagContextCreateEvent(builder));
    }

    @Override
    public ConfigurationManager getGlobalStateManager() {
        return configuration;
    }

    @Override
    public StringMatcher getMatcher() {
        return matcher;
    }

    @Override
    public SessionManager getSessionManager() {
        return sessions;
    }

    @Override
    public void broadcastNotification(String message) {
        for (LocalPlayer player : onlinePlayers()) {
            if (player.hasPermission("worldguard.notify")) {
                player.print(message);
            }
        }
        WorldGuard.logger.info(message);
    }

    @Override
    public void broadcastNotification(TextComponent component) {
        for (LocalPlayer player : onlinePlayers()) {
            if (player.hasPermission("worldguard.notify")) {
                player.print(component);
            }
        }
    }

    @Override
    public void load() {
        configuration = new NeoForgeConfiguration(getConfigDir());
        configuration.load();
        for (ServerLevel level : server().getAllLevels()) {
            configuration.getForDimensionId(NeoForgeRegionContainer.storageId(level));
        }
        refreshPermissionResolver();
        sessions = new NeoForgeSessionManager(this);
        matcher = new NeoForgeStringMatcher(this);
        regions = new NeoForgeRegionContainer();
        regions.initialize();
    }

    @Override
    public void unload() {
        if (sessions != null) {
            sessions.shutdown();
            sessions = null;
        }
        if (regions != null) {
            regions.shutdown();
            regions = null;
        }
        if (configuration != null) {
            configuration.close();
            configuration = null;
        }
        matcher = null;
        players.clear();
    }

    @Override
    public RegionContainer getRegionContainer() {
        return regions;
    }

    @Override
    public DebugHandler getDebugHandler() {
        return NeoForgeDebugHandler.INSTANCE;
    }

    @Override
    public GameMode getDefaultGameMode() {
        return GameModes.get(server().getDefaultGameType().getName());
    }

    @Override
    public Path getConfigDir() {
        if (configDir == null) {
            throw new IllegalStateException("WorldGuard server is not active");
        }
        return configDir;
    }

    @Override
    public void stackPlayerInventory(LocalPlayer localPlayer) {
        if (!(localPlayer instanceof NeoForgeLocalPlayer adapted)) {
            throw new IllegalArgumentException("Expected a NeoForge player");
        }
        var inventory = adapted.getHandle().getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack target = inventory.getItem(i);
            if (target.isEmpty() || target.getCount() >= target.getMaxStackSize()) {
                continue;
            }
            for (int j = i + 1; j < inventory.getContainerSize(); j++) {
                ItemStack source = inventory.getItem(j);
                if (source.isEmpty() || !ItemStack.isSameItemSameComponents(target, source)) {
                    continue;
                }
                int moved = Math.min(target.getMaxStackSize() - target.getCount(), source.getCount());
                target.grow(moved);
                source.shrink(moved);
                if (target.getCount() >= target.getMaxStackSize()) {
                    break;
                }
            }
        }
        adapted.getHandle().containerMenu.broadcastChanges();
    }

    @Override
    public void addPlatformReports(ReportList report) {
        // No NeoForge-specific report type is needed during bootstrap.
    }

    @Override
    public ProfileService createProfileService(ProfileCache profileCache) {
        knownProfiles = new KnownProfileService(HttpRepositoryService.forMinecraft(), Path.of("usercache.json"));
        return new CacheForwardingService(knownProfiles, profileCache);
    }
}
