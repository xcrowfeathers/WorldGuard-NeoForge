package de.kuro.worldguard.mixin;

import de.kuro.worldguard.protection.EnvironmentalHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BigDripleafBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Tilt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Checks Core's use-dripleaf flag only when an entity would first tilt the leaf. */
@Mixin(BigDripleafBlock.class)
public abstract class BigDripleafUseMixin {
    @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
    private void worldguard$tilt(BlockState state, Level level, BlockPos pos, Entity entity, CallbackInfo ci) {
        if (!(level instanceof ServerLevel server) || state.getValue(BlockStateProperties.TILT) != Tilt.NONE
                || !entity.onGround() || entity.getY() <= pos.getY() + 0.6875
                || level.hasNeighborSignal(pos)) return;
        if (!EnvironmentalHooks.dripleaf(server, pos, entity)) ci.cancel();
    }
}
