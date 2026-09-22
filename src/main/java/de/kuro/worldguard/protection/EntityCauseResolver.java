package de.kuro.worldguard.protection;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.entity.projectile.Projectile;

final class EntityCauseResolver {
    private EntityCauseResolver() {}

    static ServerPlayer player(DamageSource source) {
        ServerPlayer actor = player(source.getEntity());
        return actor != null ? actor : player(source.getDirectEntity());
    }

    static ServerPlayer player(Entity cause) {
        // Projectile owners can themselves be projectile entities in modded chains.
        for (int depth = 0; cause != null && depth < 8; depth++) {
            if (cause instanceof ServerPlayer player) return player;
            if (cause instanceof Projectile projectile) {
                Entity owner = projectile.getOwner();
                if (owner == cause) return null;
                cause = owner;
            } else if (cause instanceof TamableAnimal tameable && tameable.isTame()) {
                Entity owner = tameable.getOwner();
                if (owner == cause) return null;
                cause = owner;
            } else if (cause instanceof PrimedTnt tnt) {
                cause = tnt.getOwner();
            } else if (cause instanceof AreaEffectCloud cloud) {
                cause = cloud.getOwner();
            } else if (cause instanceof EvokerFangs fangs) {
                cause = fangs.getOwner();
            } else {
                return null;
            }
        }
        return null;
    }
}
