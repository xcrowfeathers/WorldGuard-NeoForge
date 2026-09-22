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

public final class TimedSessionActivity extends Handler {
    public static final Factory<TimedSessionActivity> FACTORY = new Factory<>() {
        @Override public TimedSessionActivity create(Session session) { return new TimedSessionActivity(session); }
    };

    private boolean active;

    private TimedSessionActivity(Session session) { super(session); }

    public boolean active() { return active; }

    @Override
    public void initialize(LocalPlayer player, Location current, ApplicableRegionSet set) {
        update(player, set);
    }

    @Override
    public void uninitialize(LocalPlayer player, Location current, ApplicableRegionSet set) {
        active = false;
    }

    @Override
    public boolean onCrossBoundary(LocalPlayer player, Location from, Location to,
                                   ApplicableRegionSet toSet, Set<ProtectedRegion> entered,
                                   Set<ProtectedRegion> exited, MoveType moveType) {
        update(player, toSet);
        return true;
    }

    private void update(LocalPlayer player, ApplicableRegionSet set) {
        Integer heal = set.queryValue(player, Flags.HEAL_AMOUNT);
        Integer feed = set.queryValue(player, Flags.FEED_AMOUNT);
        active = heal != null && heal != 0 && valid(set.queryValue(player, Flags.HEAL_DELAY))
                || feed != null && feed != 0 && valid(set.queryValue(player, Flags.FEED_DELAY));
    }

    private static boolean valid(Integer delay) { return delay != null && delay >= 0; }
}
