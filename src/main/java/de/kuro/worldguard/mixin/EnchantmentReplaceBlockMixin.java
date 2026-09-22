package de.kuro.worldguard.mixin;

import de.kuro.worldguard.protection.EnvironmentalHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.effects.ReplaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ReplaceBlock.class)
public abstract class EnchantmentReplaceBlockMixin {
    @Redirect(method = "apply", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean worldguard$frostWalker(ServerLevel level, BlockPos pos, BlockState state,
                                            ServerLevel originalLevel, int enchantmentLevel,
                                            EnchantedItemInUse item, Entity entity, Vec3 origin) {
        return EnvironmentalHooks.enchantmentPlace(level, pos, state, entity)
                && level.setBlockAndUpdate(pos, state);
    }
}
