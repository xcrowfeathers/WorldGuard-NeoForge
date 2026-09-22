package de.kuro.worldguard.platform;

import com.sk89q.worldedit.util.Location;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.QueryCache;
import com.sk89q.worldguard.protection.regions.RegionQuery.QueryOption;

/** Queries Core's indexed region state without retaining stale position snapshots.
 * Core's manager already uses a chunk hash table and spatial tree. This is one
 * indexed lookup, including immediately after mutations made through the public API.
 */
final class CurrentRegionQueryCache extends QueryCache {
    @Override
    public ApplicableRegionSet queryContains(RegionManager manager, Location location, QueryOption option) {
        return manager.getApplicableRegions(location.toVector().toBlockPoint(), option);
    }
}