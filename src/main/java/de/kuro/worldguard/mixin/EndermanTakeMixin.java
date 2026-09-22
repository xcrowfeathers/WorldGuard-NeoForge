package de.kuro.worldguard.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.sk89q.worldguard.protection.flags.Flags;
import de.kuro.worldguard.protection.EnvironmentalHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.EnderMan;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.world.entity.monster.EnderMan$EndermanTakeBlockGoal")
public abstract class EndermanTakeMixin {
    @Shadow @Final private EnderMan enderman;

    @Inject(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;removeBlock(Lnet/minecraft/core/BlockPos;Z)Z"), cancellable = true)
    private void worldguard$take(CallbackInfo ci, @Local BlockPos target) {
        if (enderman.level() instanceof ServerLevel level
                && !EnvironmentalHooks.mobBlock(level, enderman, target, Flags.ENDER_BUILD, false)) ci.cancel();
    }
}
