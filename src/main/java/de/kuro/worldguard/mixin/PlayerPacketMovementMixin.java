package de.kuro.worldguard.mixin;

import com.sk89q.worldedit.util.Location;
import de.kuro.worldguard.protection.SessionMovement;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class PlayerPacketMovementMixin {
    @Shadow public ServerPlayer player;

    @Inject(method = "handleMovePlayer", at = @At("HEAD"), cancellable = true)
    private void worldguard$beforeMove(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        SessionMovement movement = SessionMovement.installed();
        if (movement == null) return;
        Location previous = movement.packetMove(player, packet.getX(player.getX()),
                packet.getY(player.getY()), packet.getZ(player.getZ()),
                packet.getYRot(player.getYRot()), packet.getXRot(player.getXRot()));
        if (previous != null) {
            player.connection.teleport(previous.getX(), previous.getY(), previous.getZ(),
                    previous.getYaw(), previous.getPitch());
            ci.cancel();
        }
    }
}
