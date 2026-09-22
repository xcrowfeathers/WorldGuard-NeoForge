package de.kuro.worldguard.api;

import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import net.neoforged.bus.api.Event;

public final class WorldGuardFlagRegistrationEvent extends Event {
    private final FlagRegistry registry;

    public WorldGuardFlagRegistrationEvent(FlagRegistry registry) {
        this.registry = registry;
    }

    public FlagRegistry getRegistry() {
        return registry;
    }
}
