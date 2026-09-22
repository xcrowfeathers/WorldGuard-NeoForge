package de.kuro.worldguard.protection;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class EntityInteractionPair {
    private final Map<UUID, Attempt> specific = new HashMap<>();

    void record(ServerPlayer player, Entity target, InteractionHand hand) {
        specific.put(player.getUUID(), new Attempt(target.getId(), hand, player.serverLevel().getGameTime()));
    }

    boolean alreadyChecked(ServerPlayer player, Entity target, InteractionHand hand) {
        Attempt earlier = specific.remove(player.getUUID());
        return earlier != null && earlier.entityId == target.getId()
                && earlier.hand == hand && earlier.tick == player.serverLevel().getGameTime();
    }

    void forget(ServerPlayer player) {
        specific.remove(player.getUUID());
    }

    void clear() {
        specific.clear();
    }

    private record Attempt(int entityId, InteractionHand hand, long tick) { }
}
