package de.kuro.worldguard.mixin;

import com.sk89q.worldguard.protection.flags.Flags;
import de.kuro.worldguard.protection.EnvironmentalHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.CoralBlock;
import net.minecraft.world.level.block.CoralFanBlock;
import net.minecraft.world.level.block.CoralPlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({CoralBlock.class, CoralFanBlock.class, CoralPlantBlock.class})
public abstract class CoralFadeMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void worldguard$coral(BlockState state, ServerLevel level, BlockPos pos,
                                  RandomSource random, CallbackInfo ci) {
        var service = EnvironmentalHooks.service();
        if (service != null && service.active()
                && !EnvironmentalHooks.natural(level, pos, Flags.CORAL_FADE,
                service.config(level).disableCoralBlockFade)) ci.cancel();
    }
}
