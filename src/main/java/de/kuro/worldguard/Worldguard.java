package de.kuro.worldguard;

import de.kuro.worldguard.lifecycle.WorldGuardLifecycle;
import net.neoforged.fml.common.Mod;

@Mod(Worldguard.MOD_ID)
public final class Worldguard {
    public static final String MOD_ID = "worldguard";

    public Worldguard() {
        new WorldGuardLifecycle().register();
    }
}
