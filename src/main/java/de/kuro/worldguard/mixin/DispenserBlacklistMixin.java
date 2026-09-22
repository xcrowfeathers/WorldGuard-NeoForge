package de.kuro.worldguard.mixin;

import de.kuro.worldguard.protection.BlacklistProtectionListener;
import net.minecraft.core.BlockPos;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(DispenserBlock.class)
public abstract class DispenserBlacklistMixin {
    @Shadow
    protected abstract DispenseItemBehavior getDispenseMethod(Level level, ItemStack stack);

    @Redirect(method = "dispenseFrom", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/DispenserBlock;getDispenseMethod(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/core/dispenser/DispenseItemBehavior;"))
    private DispenseItemBehavior worldguard$method(DispenserBlock self, Level level, ItemStack stack,
                                                   ServerLevel server, BlockState state, BlockPos pos) {
        return BlacklistProtectionListener.allowDispense(server, pos, stack)
                ? getDispenseMethod(level, stack) : DispenseItemBehavior.NOOP;
    }
}
