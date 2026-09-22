package de.kuro.worldguard.mixin;

import com.sk89q.worldguard.protection.flags.Flags;
import de.kuro.worldguard.protection.EnvironmentalHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BuddingAmethystBlock;
import net.minecraft.world.level.block.SpreadingSnowyDirtBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin({SpreadingSnowyDirtBlock.class, BuddingAmethystBlock.class})
public abstract class NaturalSpreadMixin {
    @Redirect(method = "randomTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean worldguard$spread(ServerLevel level, BlockPos target, BlockState state) {
        var service = EnvironmentalHooks.service();
        if (service != null && service.active()) {
            var cfg = service.config(level);
            if (state.is(Blocks.GRASS_BLOCK)
                    && !EnvironmentalHooks.natural(level, target, Flags.GRASS_SPREAD, cfg.disableGrassGrowth)) return false;
            if (state.is(Blocks.MYCELIUM)
                    && !EnvironmentalHooks.natural(level, target, Flags.MYCELIUM_SPREAD, cfg.disableMyceliumSpread)) return false;
            if (state.getBlock() instanceof net.minecraft.world.level.block.AmethystClusterBlock
                    && !EnvironmentalHooks.natural(level, target, Flags.ROCK_GROWTH, cfg.disableRockGrowth)) return false;
        }
        return level.setBlockAndUpdate(target, state);
    }
}
