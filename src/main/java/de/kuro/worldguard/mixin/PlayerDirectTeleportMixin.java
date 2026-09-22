package de.kuro.worldguard.mixin;

import de.kuro.worldguard.protection.SessionMovement;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Covers command, WorldEdit, and mod teleports using ServerPlayer.teleportTo. */
@Mixin(ServerPlayer.class)
public abstract class PlayerDirectTeleportMixin {
    @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDFF)V",
            at = @At("HEAD"), cancellable = true)
    private void worldguard$beforeDirectTeleport(ServerLevel level, double x, double y, double z,
                                                  float yaw, float pitch, CallbackInfo ci) {
        SessionMovement movement = SessionMovement.installed();
        if (movement != null && !movement.dimensionTeleport((ServerPlayer) (Object) this,
                level, x, y, z, yaw, pitch)) ci.cancel();
    }
}
