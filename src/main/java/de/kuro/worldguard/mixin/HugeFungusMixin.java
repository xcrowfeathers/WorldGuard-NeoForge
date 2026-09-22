package de.kuro.worldguard.mixin;

import de.kuro.worldguard.protection.FeaturePlacementGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.FungusBlock;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** NeoForge's feature event is followed by this lambda on fungus bonemeal. */
@Mixin(FungusBlock.class)
public abstract class HugeFungusMixin {
    @Redirect(method = "lambda$performBonemeal$3", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/feature/ConfiguredFeature;place(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;)Z"))
    private static boolean worldguard$atomicFungus(ConfiguredFeature<?, ?> feature, WorldGenLevel level,
                                                     ChunkGenerator generator, RandomSource random, BlockPos pos) {
        return FeaturePlacementGuard.place(feature, level, generator, random, pos);
    }
}
