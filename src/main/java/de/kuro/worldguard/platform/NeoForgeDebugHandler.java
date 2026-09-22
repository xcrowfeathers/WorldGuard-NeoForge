package de.kuro.worldguard.platform;

import com.sk89q.minecraft.util.commands.CommandException;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.internal.platform.DebugHandler;

enum NeoForgeDebugHandler implements DebugHandler {
    INSTANCE;

    private CommandException unavailable() {
        return new CommandException("WorldGuard protection debug simulation is not available yet.");
    }

    @Override
    public void testBreak(Actor sender, LocalPlayer target, boolean fromTarget, boolean stackTraceMode) throws CommandException {
        throw unavailable();
    }

    @Override
    public void testPlace(Actor sender, LocalPlayer target, boolean fromTarget, boolean stackTraceMode) throws CommandException {
        throw unavailable();
    }

    @Override
    public void testInteract(Actor sender, LocalPlayer target, boolean fromTarget, boolean stackTraceMode) throws CommandException {
        throw unavailable();
    }

    @Override
    public void testDamage(Actor sender, LocalPlayer target, boolean fromTarget, boolean stackTraceMode) throws CommandException {
        throw unavailable();
    }
}
