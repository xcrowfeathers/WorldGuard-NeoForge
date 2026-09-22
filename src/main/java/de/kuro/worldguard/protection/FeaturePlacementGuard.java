package de.kuro.worldguard.protection;

import com.sk89q.worldguard.protection.association.RegionAssociable;
import com.sk89q.worldguard.protection.flags.Flags;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.neoforged.neoforge.common.util.BlockSnapshot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FeaturePlacementGuard {
    private FeaturePlacementGuard() {}

    public static boolean place(ConfiguredFeature<?, ?> feature, WorldGenLevel world,
                                ChunkGenerator generator, RandomSource random, BlockPos origin) {
        if (!(world instanceof ServerLevel level)) return feature.place(world, generator, random, origin);
        EnvironmentalProtection service = EnvironmentalHooks.service();
        if (service == null || !service.active()) return feature.place(world, generator, random, origin);
        EnvironmentalProtection.Context context = service.context(level);
        if (!context.config().useRegions) return feature.place(world, generator, random, origin);

        int first = level.capturedBlockSnapshots.size();
        boolean wasCapturing = level.captureBlockSnapshots;
        level.captureBlockSnapshots = true;
        boolean placed;
        try {
            placed = feature.place(world, generator, random, origin);
        } catch (RuntimeException | Error failure) {
            level.captureBlockSnapshots = wasCapturing;
            rollback(level, takeSnapshots(level, first));
            throw failure;
        } finally {
            level.captureBlockSnapshots = wasCapturing;
        }

        List<BlockSnapshot> snapshots = takeSnapshots(level, first);
        if (!placed) {
            rollback(level, snapshots);
            return false;
        }
        Map<BlockPos, BlockSnapshot> originals = new LinkedHashMap<>();
        for (BlockSnapshot snapshot : snapshots) originals.putIfAbsent(snapshot.getPos(), snapshot);
        RegionAssociable source = context.source(origin);
        for (BlockSnapshot original : originals.values()) {
            BlockPos target = original.getPos();
            if (!original.getState().equals(level.getBlockState(target))
                    && !context.build(target, source, Flags.BLOCK_PLACE)) {
                rollback(level, snapshots);
                return false;
            }
        }

        if (wasCapturing) {
            level.capturedBlockSnapshots.addAll(snapshots);
        } else {
            for (BlockSnapshot snapshot : snapshots) {
                BlockPos pos = snapshot.getPos();
                var newState = level.getBlockState(pos);
                newState.onPlace(level, pos, snapshot.getState(), false);
                level.markAndNotifyBlock(pos, level.getChunkAt(pos), snapshot.getState(),
                        newState, snapshot.getFlags(), 512);
            }
        }
        return true;
    }

    private static List<BlockSnapshot> takeSnapshots(ServerLevel level, int first) {
        List<BlockSnapshot> result = new ArrayList<>(level.capturedBlockSnapshots.subList(first,
                level.capturedBlockSnapshots.size()));
        level.capturedBlockSnapshots.subList(first, level.capturedBlockSnapshots.size()).clear();
        return result;
    }

    private static void rollback(ServerLevel level, List<BlockSnapshot> snapshots) {
        boolean capturing = level.captureBlockSnapshots;
        boolean restoring = level.restoringBlockSnapshots;
        level.captureBlockSnapshots = false;
        level.restoringBlockSnapshots = true;
        try {
            for (int i = snapshots.size() - 1; i >= 0; i--) {
                BlockSnapshot snapshot = snapshots.get(i);
                snapshot.restore(snapshot.getFlags() | net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
            }
        } finally {
            level.captureBlockSnapshots = capturing;
            level.restoringBlockSnapshots = restoring;
        }
    }
}
