package de.kuro.worldguard.mixin;

import de.kuro.worldguard.protection.EnvironmentalHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FireBlock.class)
public abstract class FireBlockMixin {
    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private boolean worldguard$spread(ServerLevel level, BlockPos target, BlockState state, int flags) {
        // Updating an existing fire block's age is not a spread event.
        if (!level.getBlockState(target).getBlock().equals(state.getBlock())
                && !EnvironmentalHooks.fire(level, target, false)) return false;
        return level.setBlock(target, state, flags);
    }

    @Inject(method = "checkBurnOut", at = @At("HEAD"), cancellable = true)
    private void worldguard$burn(Level level, BlockPos target, int chance, RandomSource random,
                                 int age, net.minecraft.core.Direction face, CallbackInfo ci) {
        if (level instanceof ServerLevel server && !EnvironmentalHooks.fireBurn(server, target)) ci.cancel();
    }
}
