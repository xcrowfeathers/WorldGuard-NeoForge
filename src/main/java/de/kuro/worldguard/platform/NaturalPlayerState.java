package de.kuro.worldguard.platform;

import com.sk89q.worldedit.util.Location;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.session.MoveType;
import com.sk89q.worldguard.session.Session;
import com.sk89q.worldguard.session.handler.Handler;

import java.util.Set;

/** Effective natural player rules are cached within the Core session. */
public final class NaturalPlayerState extends Handler {
    public static final Factory<NaturalPlayerState> FACTORY = new Factory<>() {
        @Override public NaturalPlayerState create(Session session) { return new NaturalPlayerState(session); }
    };

    private boolean healthRegen = true;
    private boolean hungerDrain = true;

    private NaturalPlayerState(Session session) { super(session); }
    public boolean healthRegen() { return healthRegen; }
    public boolean hungerDrain() { return hungerDrain; }

    @Override
    public void initialize(LocalPlayer player, Location current, ApplicableRegionSet set) { update(player, set); }

    @Override
    public boolean onCrossBoundary(LocalPlayer player, Location from, Location to,
                                   ApplicableRegionSet toSet, Set<ProtectedRegion> entered,
                                   Set<ProtectedRegion> exited, MoveType moveType) {
        update(player, toSet);
        return true;
    }

    private void update(LocalPlayer player, ApplicableRegionSet set) {
        healthRegen = set.testState(player, Flags.HEALTH_REGEN);
        hungerDrain = set.testState(player, Flags.HUNGER_DRAIN);
    }
}
