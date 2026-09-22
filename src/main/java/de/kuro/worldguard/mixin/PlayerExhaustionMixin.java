package de.kuro.worldguard.mixin;

import de.kuro.worldguard.protection.SessionPlayerFlagsListener;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Stops vanilla activity exhaustion before it is stored in FoodData. */
@Mixin(Player.class)
public abstract class PlayerExhaustionMixin {
    @Inject(method = "causeFoodExhaustion", at = @At("HEAD"), cancellable = true)
    private void worldguard$hungerDrain(float amount, CallbackInfo ci) {
        if (!SessionPlayerFlagsListener.naturalHunger((Player) (Object) this)) ci.cancel();
    }
}
