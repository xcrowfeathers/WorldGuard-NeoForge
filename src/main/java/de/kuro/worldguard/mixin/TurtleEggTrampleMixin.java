package de.kuro.worldguard.mixin;

import de.kuro.worldguard.protection.EnvironmentalHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TurtleEggBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Checks the trample decision before vanilla rolls to crack a turtle egg. */
@Mixin(TurtleEggBlock.class)
public abstract class TurtleEggTrampleMixin {
    @Inject(method = "destroyEgg", at = @At("HEAD"), cancellable = true)
    private void worldguard$trample(Level level, BlockState state, BlockPos pos, Entity actor,
                                    int chance, CallbackInfo ci) {
        if (level instanceof ServerLevel server && !EnvironmentalHooks.turtleEggTrample(server, pos, actor)) ci.cancel();
    }
}
