package de.kuro.worldguard.api;

import com.sk89q.worldguard.protection.flags.FlagContext;
import net.neoforged.bus.api.Event;

public final class FlagContextCreateEvent extends Event {
    private final FlagContext.FlagContextBuilder builder;

    public FlagContextCreateEvent(FlagContext.FlagContextBuilder builder) {
        this.builder = builder;
    }

    public FlagContext.FlagContextBuilder getBuilder() {
        return builder;
    }
}
