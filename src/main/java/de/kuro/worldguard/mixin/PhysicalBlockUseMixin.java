package de.kuro.worldguard.mixin;

import de.kuro.worldguard.protection.EnvironmentalHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.TripWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({BasePressurePlateBlock.class, TripWireBlock.class})
public abstract class PhysicalBlockUseMixin {
    @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
    private void worldguard$physical(BlockState state, Level level, BlockPos pos, Entity entity, CallbackInfo ci) {
        if (!(level instanceof ServerLevel server)) return;
        if (state.hasProperty(BlockStateProperties.POWERED) && state.getValue(BlockStateProperties.POWERED)) return;
        if (state.hasProperty(BlockStateProperties.POWER) && state.getValue(BlockStateProperties.POWER) > 0) return;
        if (!EnvironmentalHooks.physicalUse(server, pos, entity)) ci.cancel();
    }
}
