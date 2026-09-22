package de.kuro.worldguard.lifecycle;

import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.command.util.FutureProgressListener;
import com.sk89q.worldedit.event.platform.PlatformReadyEvent;
import com.sk89q.worldedit.event.platform.PlatformUnreadyEvent;
import com.sk89q.worldedit.neoforge.NeoForgeAdapter;
import com.sk89q.worldedit.neoforge.NeoForgeWorldEdit;
import com.sk89q.worldedit.util.eventbus.Subscribe;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.registry.SimpleFlagRegistry;
import de.kuro.worldguard.api.WorldGuardFlagRegistrationEvent;
import de.kuro.worldguard.platform.NeoForgeWorldGuardPlatform;
import de.kuro.worldguard.commands.RegionCommandBridge;
import de.kuro.worldguard.protection.PlayerProtectionListener;
import de.kuro.worldguard.protection.BlacklistProtectionListener;
import de.kuro.worldguard.protection.BuildPermissionListener;
import de.kuro.worldguard.protection.BlockedPotionListener;
import de.kuro.worldguard.protection.EntityProtectionListener;
import de.kuro.worldguard.protection.EnvironmentalProtection;
import de.kuro.worldguard.protection.EnvironmentalProtectionListener;
import de.kuro.worldguard.protection.EnvironmentalHooks;
import de.kuro.worldguard.protection.ProtectionDecisionService;
import de.kuro.worldguard.protection.SessionMovement;
import de.kuro.worldguard.protection.SessionPlayerFlagsListener;
import com.sk89q.worldguard.session.MoveType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.neoforged.fml.ModList;

import java.util.logging.Level;
import java.lang.reflect.Field;
import java.util.Timer;

/** Starts Core only after WorldEdit declares its NeoForge platform ready. */
public final class WorldGuardLifecycle {
    private final NeoForgeWorldGuardPlatform platform = new NeoForgeWorldGuardPlatform();
    private final RegionCommandBridge regionCommands = new RegionCommandBridge(platform);
    private final ProtectionDecisionService decisions = new ProtectionDecisionService(platform);
    private final PlayerProtectionListener protection = new PlayerProtectionListener(decisions);
    private final BlacklistProtectionListener blacklist = new BlacklistProtectionListener(platform);
    private final BuildPermissionListener buildPermissions = new BuildPermissionListener(platform);
    private final BlockedPotionListener blockedPotions = new BlockedPotionListener(platform);
    private final EntityProtectionListener entityProtection = new EntityProtectionListener(decisions);
    private final EnvironmentalProtection environmental = new EnvironmentalProtection(platform);
    private final EnvironmentalProtectionListener environmentalListener = new EnvironmentalProtectionListener(environmental);
    private final SessionMovement movement = new SessionMovement(platform);
    private final SessionPlayerFlagsListener sessionFlags = new SessionPlayerFlagsListener(platform, decisions, movement);
    private MinecraftServer activeServer;
    private int sessionTicks;

    public void register() {
        EnvironmentalHooks.install(environmental, decisions);
        movement.install();
        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(protection);
        NeoForge.EVENT_BUS.register(blacklist);
        NeoForge.EVENT_BUS.register(buildPermissions);
        NeoForge.EVENT_BUS.register(blockedPotions);
        NeoForge.EVENT_BUS.register(entityProtection);
        NeoForge.EVENT_BUS.register(environmentalListener);
        NeoForge.EVENT_BUS.register(sessionFlags);
        WorldEdit.getInstance().getEventBus().register(this);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        regionCommands.register(event);
    }

    @Subscribe
    public void onWorldEditReady(PlatformReadyEvent event) {
        if (NeoForgeWorldEdit.inst == null || event.getPlatform() != NeoForgeWorldEdit.inst.getPlatform()) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            throw new IllegalStateException("WorldEdit became ready without a Minecraft server");
        }
        if (activeServer == server) {
            return;
        }
        if (activeServer != null) {
            stop();
        }

        platform.bind(server);
        WorldGuard guard = WorldGuard.getInstance();
        guard.setPlatform(platform);
        try {
            ensureCoreVersion();
            SimpleFlagRegistry registry = (SimpleFlagRegistry) guard.getFlagRegistry();
            if (!registry.isInitialized()) {
                NeoForge.EVENT_BUS.post(new WorldGuardFlagRegistrationEvent(registry));
            }
            guard.setup();
            registry.setInitialized(true);
            activeServer = server;
            WorldGuard.logger.info("WorldGuard Core initialized for NeoForge server with "
                    + platform.getRegionContainer().getLoaded().size() + " dimension managers");
        } catch (RuntimeException error) {
            WorldGuard.logger.log(Level.SEVERE, "WorldGuard failed to initialize; stopping the server", error);
            try {
                guard.disable();
            } finally {
                platform.unbind();
                server.halt(false);
            }
            throw error;
        }
    }

    /** EngineHub's Core artifact has no Implementation-Version manifest entry. */
    private static void ensureCoreVersion() {
        if (!"(unknown)".equals(WorldGuard.getVersion())) return;
        String portVersion = ModList.get().getModContainerById("worldguard")
                .orElseThrow().getModInfo().getVersion().toString();
        String coreVersion = portVersion.split("-neoforge", 2)[0];
        try {
            Field field = WorldGuard.class.getDeclaredField("version");
            field.setAccessible(true);
            field.set(null, coreVersion);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot expose the packaged WorldGuard Core version", error);
        }
    }

    @Subscribe
    public void onWorldEditUnready(PlatformUnreadyEvent event) {
        if (NeoForgeWorldEdit.inst != null && event.getPlatform() == NeoForgeWorldEdit.inst.getPlatform()) {
            stop();
        }
    }

    @SubscribeEvent
    public void onLevelLoad(LevelEvent.Load event) {
        if (activeServer != null && event.getLevel() instanceof ServerLevel level
                && level.getServer() == activeServer) {
            platform.loadLevel(NeoForgeAdapter.adapt(level));
        }
    }

    @SubscribeEvent
    public void onLevelUnload(LevelEvent.Unload event) {
        if (activeServer != null && event.getLevel() instanceof ServerLevel level
                && level.getServer() == activeServer) {
            platform.unloadLevel(NeoForgeAdapter.adapt(level));
        }
    }

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        if (activeServer != null && event.getLevel() instanceof ServerLevel level
                && level.getServer() == activeServer) {
            var pos = event.getChunk().getPos();
            platform.loadChunk(level, pos.x, pos.z);
        }
    }

    @SubscribeEvent
    public void onChunkUnload(ChunkEvent.Unload event) {
        if (activeServer != null && event.getLevel() instanceof ServerLevel level
                && level.getServer() == activeServer) {
            var pos = event.getChunk().getPos();
            platform.unloadChunk(level, pos.x, pos.z);
        }
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        // The position check only
        // enters Core movement processing when a player's block changes.
        if (activeServer != null) {
            for (var player : activeServer.getPlayerList().getPlayers()) movement.observedTick(player);

            if (++sessionTicks == 20) {
                sessionTicks = 0;
                platform.tickSessions();
            }
        }
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        if (event.getServer() == activeServer) {
            stop();
        }
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (activeServer != null && event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            platform.rememberProfile(player);
            platform.getSessionManager().get(platform.adapt(player));
            movement.observe(player);
        }
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (activeServer != null && event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            movement.forget(player);
            platform.removePlayer(player);
            entityProtection.forget(player);
            blacklist.forget(player);
            buildPermissions.forget(player);
        }
    }

    @SubscribeEvent
    public void onPlayerClone(PlayerEvent.Clone event) {
        if (activeServer != null && event.getOriginal() instanceof net.minecraft.server.level.ServerPlayer oldPlayer) {
            var oldLocal = platform.adapt(oldPlayer);
            var oldSession = platform.getSessionManager().getIfPresent(oldLocal);
            var modeHandler = oldSession == null ? null
                    : oldSession.getHandler(com.sk89q.worldguard.session.handler.GameModeFlag.class);
            var intendedMode = modeHandler == null ? null : modeHandler.getOriginalGameMode();
            movement.forget(oldPlayer);
            platform.removePlayer(oldPlayer);
            if (intendedMode != null && event.getEntity() instanceof net.minecraft.server.level.ServerPlayer newPlayer) {
                newPlayer.setGameMode(net.minecraft.world.level.GameType.byName(intendedMode.id()));
            }
        }
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (activeServer != null && event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            movement.afterNonCancellableMove(player, MoveType.RESPAWN);
        }
    }

    @SubscribeEvent
    public void onPlayerDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (activeServer != null && event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            movement.afterNonCancellableMove(player, MoveType.OTHER_NON_CANCELLABLE);
        }
    }

    private void stop() {
        if (activeServer == null) {
            return;
        }
        boolean dedicated = activeServer.isDedicatedServer();
        try {
            WorldGuard.getInstance().disable();
        } finally {
            activeServer = null;
            protection.reset();
            entityProtection.reset();
            blacklist.clear();
            buildPermissions.clear();
            movement.clear();
            sessionTicks = 0;
            platform.unbind();
            if (dedicated) {
                cancelWorldEditProgressTimer();
            }
        }
    }

    /** WorldEdit 7.3.8 has no public shutdown hook for this non-daemon static timer. */
    private static void cancelWorldEditProgressTimer() {
        try {
            Field timerField = FutureProgressListener.class.getDeclaredField("timer");
            timerField.setAccessible(true);
            ((Timer) timerField.get(null)).cancel();
        } catch (ReflectiveOperationException | SecurityException error) {
            WorldGuard.logger.log(Level.WARNING,
                    "Could not stop WorldEdit's async-command progress timer on dedicated shutdown", error);
        }
    }
}
