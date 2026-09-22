package de.kuro.worldguard.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.sk89q.worldguard.protection.flags.Flags;
import de.kuro.worldguard.protection.EnvironmentalHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.SculkSpreader;
import net.minecraft.world.level.block.state.BlockState;
import java.util.Collection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(net.minecraft.world.level.block.SculkVeinBlock.class)
public abstract class SculkVeinMixin {
    @Inject(method = "regrow", at = @At("HEAD"), cancellable = true)
    private static void worldguard$vein(LevelAccessor level, BlockPos pos, BlockState state,
                                        Collection<Direction> directions, CallbackInfoReturnable<Boolean> cir) {
        var service = EnvironmentalHooks.service();
        if (level instanceof ServerLevel server && service != null && service.active()
                && !EnvironmentalHooks.natural(server, pos, Flags.SCULK_GROWTH,
                service.config(server).disableSculkGrowth)) cir.setReturnValue(false);
    }

    @Inject(method = "attemptPlaceSculk", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/LevelAccessor;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"), cancellable = true)
    private void worldguard$convert(SculkSpreader spreader, LevelAccessor level, BlockPos pos,
                                    RandomSource random, CallbackInfoReturnable<Boolean> cir,
                                    @Local(ordinal = 1) BlockPos target) {
        var service = EnvironmentalHooks.service();
        if (level instanceof ServerLevel server && service != null && service.active()
                && !EnvironmentalHooks.natural(server, target, Flags.SCULK_GROWTH,
                service.config(server).disableSculkGrowth)) cir.setReturnValue(false);
    }
}
