package de.kuro.worldguard.mixin;

import com.sk89q.worldguard.protection.flags.Flags;
import de.kuro.worldguard.protection.EnvironmentalHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FarmBlock.class)
public abstract class FarmDryMixin {
    @Inject(method = "turnToDirt", at = @At("HEAD"), cancellable = true)
    private static void worldguard$dry(Entity cause, BlockState state, Level level,
                                       BlockPos pos, CallbackInfo ci) {
        // A non-null cause is trample damage, handled by the player/entity path.
        if (cause != null || !(level instanceof ServerLevel server)) return;
        var service = EnvironmentalHooks.service();
        if (service != null && service.active()
                && !EnvironmentalHooks.natural(server, pos, Flags.SOIL_DRY,
                service.config(server).disableSoilDehydration)) ci.cancel();
    }
}
