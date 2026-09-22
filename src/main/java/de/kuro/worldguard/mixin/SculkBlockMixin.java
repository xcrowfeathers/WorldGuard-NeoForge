package de.kuro.worldguard.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.sk89q.worldguard.protection.flags.Flags;
import de.kuro.worldguard.protection.EnvironmentalHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.SculkBlock;
import net.minecraft.world.level.block.SculkSpreader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SculkBlock.class)
public abstract class SculkBlockMixin {
    @Inject(method = "attemptUseCharge", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/LevelAccessor;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"), cancellable = true)
    private void worldguard$growth(SculkSpreader.ChargeCursor cursor, LevelAccessor level,
                                   BlockPos pos, RandomSource random, SculkSpreader spreader,
                                   boolean shouldConvertBlocks, CallbackInfoReturnable<Integer> cir,
                                   @Local(ordinal = 2) BlockPos target) {
        var service = EnvironmentalHooks.service();
        if (level instanceof ServerLevel server && service != null && service.active()
                && !EnvironmentalHooks.natural(server, target, Flags.SCULK_GROWTH,
                service.config(server).disableSculkGrowth)) {
            cir.setReturnValue(cursor.getCharge());
        }
    }
}
