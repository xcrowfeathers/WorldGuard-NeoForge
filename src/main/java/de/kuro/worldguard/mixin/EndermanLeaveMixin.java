package de.kuro.worldguard.mixin;

import com.sk89q.worldguard.protection.flags.Flags;
import de.kuro.worldguard.protection.EnvironmentalHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.entity.monster.EnderMan$EndermanLeaveBlockGoal")
public abstract class EndermanLeaveMixin {
    @Shadow @Final private EnderMan enderman;

    @Inject(method = "canPlaceBlock", at = @At("HEAD"), cancellable = true)
    private void worldguard$leave(Level level, BlockPos target, BlockState carried,
                                  BlockState existing, BlockState below, BlockPos belowPos,
                                  CallbackInfoReturnable<Boolean> cir) {
        if (level instanceof ServerLevel server
                && !EnvironmentalHooks.mobBlock(server, enderman, target, Flags.ENDER_BUILD, true)) {
            cir.setReturnValue(false);
        }
    }
}
