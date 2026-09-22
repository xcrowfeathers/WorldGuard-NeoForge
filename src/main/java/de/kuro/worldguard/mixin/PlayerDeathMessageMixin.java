package de.kuro.worldguard.mixin;

import de.kuro.worldguard.protection.EnvironmentalHooks;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerPlayer.class)
public abstract class PlayerDeathMessageMixin {
    @Redirect(method = "die", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/players/PlayerList;broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V"))
    private void worldguard$broadcastAll(PlayerList list, Component message, boolean overlay) {
        if (EnvironmentalHooks.showDeathMessage((ServerPlayer) (Object) this)) {
            list.broadcastSystemMessage(message, overlay);
        }
    }

    @Redirect(method = "die", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/players/PlayerList;broadcastSystemToTeam(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/network/chat/Component;)V"))
    private void worldguard$broadcastTeam(PlayerList list, Player player, Component message) {
        if (EnvironmentalHooks.showDeathMessage((ServerPlayer) (Object) this)) {
            list.broadcastSystemToTeam(player, message);
        }
    }

    @Redirect(method = "die", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/players/PlayerList;broadcastSystemToAllExceptTeam(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/network/chat/Component;)V"))
    private void worldguard$broadcastOtherTeam(PlayerList list, Player player, Component message) {
        if (EnvironmentalHooks.showDeathMessage((ServerPlayer) (Object) this)) {
            list.broadcastSystemToAllExceptTeam(player, message);
        }
    }
}
