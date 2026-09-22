package de.kuro.worldguard.mixin;

import de.kuro.worldguard.protection.FeaturePlacementGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(MushroomBlock.class)
public abstract class HugeMushroomMixin {
    @Redirect(method = "growMushroom", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/feature/ConfiguredFeature;place(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;)Z"))
    private boolean worldguard$atomicMushroom(ConfiguredFeature<?, ?> feature, WorldGenLevel level,
                                                ChunkGenerator generator, RandomSource random, BlockPos pos) {
        return FeaturePlacementGuard.place(feature, level, generator, random, pos);
    }
}
