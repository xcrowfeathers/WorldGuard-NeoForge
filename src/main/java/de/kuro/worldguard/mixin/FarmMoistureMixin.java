package de.kuro.worldguard.mixin;

import com.sk89q.worldguard.protection.flags.Flags;
import de.kuro.worldguard.protection.EnvironmentalHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Only intercepts farmland's moisture property write, leaving dry-to-dirt checks separate. */
@Mixin(FarmBlock.class)
public abstract class FarmMoistureMixin {
    @Redirect(method = "randomTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private boolean worldguard$moisture(ServerLevel level, BlockPos pos, BlockState state, int flags) {
        var service = EnvironmentalHooks.service();
        if (service != null && service.active()
                && !EnvironmentalHooks.natural(level, pos, Flags.MOISTURE_CHANGE,
                service.config(level).disableSoilMoistureChange)) return false;
        return level.setBlock(pos, state, flags);
    }
}
