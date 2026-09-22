package de.kuro.worldguard.mixin;

import com.sk89q.worldguard.config.WorldConfiguration;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import de.kuro.worldguard.protection.EnvironmentalHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({LeavesBlock.class, IceBlock.class, SnowLayerBlock.class,
        WeatheringCopperFullBlock.class, WeatheringCopperSlabBlock.class, WeatheringCopperStairBlock.class})
public abstract class NaturalFadeMixin {
    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void worldguard$fade(BlockState state, ServerLevel level, BlockPos pos,
                                 RandomSource random, CallbackInfo ci) {
        var service = EnvironmentalHooks.service();
        if (service == null || !service.active()) return;
        WorldConfiguration cfg = service.config(level);
        StateFlag flag;
        boolean disabled;
        if (state.is(BlockTags.LEAVES)) {
            flag = Flags.LEAF_DECAY; disabled = cfg.disableLeafDecay;
        } else if (state.is(Blocks.FROSTED_ICE)) {
            flag = Flags.FROSTED_ICE_MELT; disabled = false;
        } else if (state.is(Blocks.ICE)) {
            flag = Flags.ICE_MELT; disabled = cfg.disableIceMelting;
        } else if (state.is(Blocks.SNOW)) {
            flag = Flags.SNOW_MELT; disabled = cfg.disableSnowMelting;
        } else if (state.getBlock() instanceof WeatheringCopper) {
            flag = Flags.COPPER_FADE; disabled = cfg.disableCopperBlockFade;
        } else return;
        if (!EnvironmentalHooks.natural(level, pos, flag, disabled)) ci.cancel();
    }
}
