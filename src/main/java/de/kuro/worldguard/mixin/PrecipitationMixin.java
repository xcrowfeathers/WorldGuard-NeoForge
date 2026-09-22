package de.kuro.worldguard.mixin;

import com.sk89q.worldguard.protection.flags.Flags;
import de.kuro.worldguard.protection.EnvironmentalHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerLevel.class)
public abstract class PrecipitationMixin {
    @Redirect(method = "tickPrecipitation", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean worldguard$weatherBlock(ServerLevel level, BlockPos target, BlockState state) {
        var service = EnvironmentalHooks.service();
        if (service != null && service.active()) {
            var cfg = service.config(level);
            if (state.is(Blocks.ICE)
                    && !EnvironmentalHooks.natural(level, target, Flags.ICE_FORM, cfg.disableIceFormation)) return false;
            if (state.is(Blocks.SNOW)
                    && !EnvironmentalHooks.natural(level, target, Flags.SNOW_FALL, cfg.disableSnowFormation)) return false;
        }
        return level.setBlockAndUpdate(target, state);
    }
}
