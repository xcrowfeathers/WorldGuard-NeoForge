package de.kuro.worldguard.mixin;

import de.kuro.worldguard.protection.SessionPlayerFlagsListener;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PlayerList.class)
public abstract class PlayerChatDeliveryMixin {
    @Redirect(method = "broadcastChatMessage(Lnet/minecraft/network/chat/PlayerChatMessage;Ljava/util/function/Predicate;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/network/chat/ChatType$Bound;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;sendChatMessage(Lnet/minecraft/network/chat/OutgoingChatMessage;ZLnet/minecraft/network/chat/ChatType$Bound;)V"))
    private void worldguard$filterChat(ServerPlayer recipient, OutgoingChatMessage message,
                                       boolean filtered, ChatType.Bound bound) {
        if (SessionPlayerFlagsListener.receiveChat(recipient)) {
            recipient.sendChatMessage(message, filtered, bound);
        }
    }
}
