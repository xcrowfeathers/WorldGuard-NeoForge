package de.kuro.worldguard.mixin;

import com.sk89q.worldguard.protection.flags.Flags;
import de.kuro.worldguard.protection.EnvironmentalHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** MultifaceSpreader is shared with lichen; only sculk-vein placements are checked. */
@Mixin(targets = "net.minecraft.world.level.block.MultifaceSpreader$SpreadConfig")
public interface SculkVeinSpreadMixin {
    @Redirect(method = "placeBlock", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/LevelAccessor;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private boolean worldguard$sculk(LevelAccessor level, BlockPos target, BlockState state, int flags) {
        var service = EnvironmentalHooks.service();
        if (state.is(Blocks.SCULK_VEIN) && level instanceof ServerLevel server
                && service != null && service.active()
                && !EnvironmentalHooks.natural(server, target, Flags.SCULK_GROWTH,
                service.config(server).disableSculkGrowth)) return false;
        return level.setBlock(target, state, flags);
    }
}
