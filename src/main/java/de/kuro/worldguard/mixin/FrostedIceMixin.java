package de.kuro.worldguard.mixin;

import com.sk89q.worldguard.protection.flags.Flags;
import de.kuro.worldguard.protection.EnvironmentalHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FrostedIceBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FrostedIceBlock.class)
public abstract class FrostedIceMixin {
    @Inject(method = "slightlyMelt", at = @At("HEAD"), cancellable = true)
    private void worldguard$melt(BlockState state, Level level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (level instanceof ServerLevel server && !EnvironmentalHooks.natural(server, pos,
                Flags.FROSTED_ICE_MELT, false)) cir.setReturnValue(false);
    }

    @Inject(method = "neighborChanged", at = @At("HEAD"), cancellable = true)
    private void worldguard$neighborMelt(BlockState state, Level level, BlockPos pos, Block neighbor,
                                         BlockPos fromPos, boolean moving, CallbackInfo ci) {
        if (level instanceof ServerLevel server && !EnvironmentalHooks.natural(server, pos,
                Flags.FROSTED_ICE_MELT, false)) ci.cancel();
    }
}
