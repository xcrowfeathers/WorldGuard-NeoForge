package de.kuro.worldguard.mixin;

import de.kuro.worldguard.protection.SessionMovement;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.DimensionTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Check region exit/entry before a portal or other dimension transition commits. */
@Mixin(ServerPlayer.class)
public abstract class PlayerDimensionMovementMixin {
    @Inject(method = "changeDimension", at = @At("HEAD"), cancellable = true)
    private void worldguard$beforeDimensionChange(DimensionTransition transition,
                                                   CallbackInfoReturnable<Entity> result) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        SessionMovement movement = SessionMovement.installed();
        if (movement != null && !movement.dimensionTeleport(player, transition.newLevel(),
                transition.pos().x, transition.pos().y, transition.pos().z,
                transition.yRot(), transition.xRot())) {
            result.setReturnValue(player);
        }
    }
}
