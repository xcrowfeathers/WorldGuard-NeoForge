package de.kuro.worldguard.platform;

import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldedit.neoforge.NeoForgeAdapter;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.session.AbstractSessionManager;
import com.sk89q.worldguard.session.Session;
import com.sk89q.worldguard.session.handler.Handler;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.UUID;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;

final class NeoForgeSessionManager extends AbstractSessionManager {
    private final NeoForgeWorldGuardPlatform platform;
    private final Cache<BypassKey, Boolean> bypass = CacheBuilder.newBuilder()
            .maximumSize(1000).expireAfterWrite(2, TimeUnit.SECONDS).build();
    private final Cache<?, Session> coreSessions;
    private final Field cacheKeyUuid;
    private final Set<Handler.Factory<? extends Handler>> externalHandlers = new HashSet<>();

    private record BypassKey(UUID player, String dimension) { }

    NeoForgeSessionManager(NeoForgeWorldGuardPlatform platform) {
        this.platform = platform;
        try {
            Field sessions = AbstractSessionManager.class.getDeclaredField("sessions");
            sessions.setAccessible(true);
            @SuppressWarnings("unchecked") Cache<?, Session> cache = (Cache<?, Session>) sessions.get(this);
            coreSessions = cache;
            cacheKeyUuid = Class.forName(AbstractSessionManager.class.getName() + "$CacheKey")
                    .getDeclaredField("uuid");
            cacheKeyUuid.setAccessible(true);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("WorldGuard Core 7.0.12 session cache layout changed", error);
        }
        super.registerHandler(TimedSessionActivity.FACTORY, null);
        super.registerHandler(NaturalPlayerState.FACTORY, null);
    }

    @Override
    public boolean registerHandler(Handler.Factory<? extends Handler> factory,
                                   @Nullable Handler.Factory<? extends Handler> after) {
        boolean registered = super.registerHandler(factory, after);
        if (registered && factory != TimedSessionActivity.FACTORY
                && factory != NaturalPlayerState.FACTORY) externalHandlers.add(factory);
        return registered;
    }

    @Override
    public boolean unregisterHandler(Handler.Factory<? extends Handler> factory) {
        boolean removed = super.unregisterHandler(factory);
        if (removed) externalHandlers.remove(factory);
        return removed;
    }

    @Override
    public void resetAllStates() {
        bypass.invalidateAll();
        for (LocalPlayer player : platform.onlinePlayers()) {
            resetState(player);
        }
    }

    @Override
    public boolean hasBypass(LocalPlayer player, World world) {
        Session session = getIfPresent(player);
        if (session == null || session.hasBypassDisabled()) {
            return false;
        }
        String dimension = NeoForgeAdapter.adapt(world).dimension().location().toString();
        if (WorldGuard.getInstance().getPlatform().getGlobalStateManager().disablePermissionCache) {
            return player.hasPermission("worldguard.region.bypass." + dimension);
        }
        BypassKey key = new BypassKey(player.getUniqueId(), dimension);
        Boolean cached = bypass.getIfPresent(key);
        if (cached != null) {
            return cached;
        }
        boolean result = player.hasPermission("worldguard.region.bypass." + dimension);
        bypass.put(key, result);
        return result;
    }

    void remove(LocalPlayer player) {
        bypass.invalidateAll();
        Session session = getIfPresent(player);
        if (session != null) {
            session.uninitialize(player);
        }
        UUID uuid = player.getUniqueId();
        for (Object key : coreSessions.asMap().keySet()) {
            try {
                if (uuid.equals(cacheKeyUuid.get(key))) coreSessions.asMap().remove(key);
            } catch (IllegalAccessException error) {
                throw new IllegalStateException("Cannot release WorldGuard player session", error);
            }
        }
    }

    void tickTimed(LocalPlayer player) {
        Session session = getIfPresent(player);
        if (session == null) return;
        TimedSessionActivity activity = session.getHandler(TimedSessionActivity.class);
        if (!externalHandlers.isEmpty() || activity != null && activity.active()) session.tick(player);
    }

    void shutdown() {
        for (LocalPlayer player : platform.onlinePlayers()) {
            remove(player);
        }
        bypass.invalidateAll();
    }
}
