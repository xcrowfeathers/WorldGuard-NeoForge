package de.kuro.worldguard.mixin;

import com.sk89q.worldguard.protection.flags.Flags;
import de.kuro.worldguard.protection.EnvironmentalHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin({MushroomBlock.class, VineBlock.class})
public abstract class MushroomVineMixin {
    @Redirect(method = "randomTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private boolean worldguard$spread(ServerLevel level, BlockPos target, BlockState state, int flags) {
        var service = EnvironmentalHooks.service();
        if (service != null && service.active()) {
            var cfg = service.config(level);
            if (state.getBlock() instanceof MushroomBlock
                    && !EnvironmentalHooks.natural(level, target, Flags.MUSHROOMS, cfg.disableMushroomSpread)) return false;
            if (state.getBlock() instanceof VineBlock
                    && !EnvironmentalHooks.natural(level, target, Flags.VINE_GROWTH, cfg.disableVineGrowth)) return false;
        }
        return level.setBlock(target, state, flags);
    }
}
