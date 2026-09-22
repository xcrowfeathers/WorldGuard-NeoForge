package de.kuro.worldguard.protection;

import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import de.kuro.worldguard.api.ProtectionDecisionEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Saddleable;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.BlockAttachedEntity;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.vehicle.AbstractMinecartContainer;
import net.minecraft.world.entity.vehicle.ChestBoat;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.item.MinecartItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityInvulnerabilityCheckEvent;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Player-caused entity protection using the same Core decision path as block protection. */
public final class EntityProtectionListener {
    private static final StateFlag[] INTERACT = {Flags.INTERACT};
    private static final StateFlag[] RIDE = {Flags.RIDE, Flags.INTERACT};
    private static final StateFlag[] CHEST = {Flags.CHEST_ACCESS};
    private static final StateFlag[] ROTATE = {Flags.ITEM_FRAME_ROTATE};
    private static final StateFlag[] ANIMALS = {Flags.DAMAGE_ANIMALS};
    private static final StateFlag[] ANIMALS_FIREWORK = {Flags.DAMAGE_ANIMALS, Flags.FIREWORK_DAMAGE};
    private static final StateFlag[] PVP = {Flags.PVP};
    private static final StateFlag[] PVP_FIREWORK = {Flags.PVP, Flags.FIREWORK_DAMAGE};
    private static final StateFlag[] VEHICLE_PLACE = {Flags.PLACE_VEHICLE};
    private static final StateFlag[] VEHICLE_DESTROY = {Flags.DESTROY_VEHICLE};
    private static final StateFlag[] VEHICLE_DESTROY_FIREWORK = {Flags.DESTROY_VEHICLE, Flags.FIREWORK_DAMAGE};
    private static final StateFlag[] FIREWORK = {Flags.FIREWORK_DAMAGE};
    private static final StateFlag[] BUILD = {};
    private static final StateFlag[] INTERACT_FIREWORK = {Flags.INTERACT, Flags.FIREWORK_DAMAGE};

    private final ProtectionDecisionService decisions;
    private final Map<UUID, InteractionAttempt> interactionAttempts = new HashMap<>();
    private final Map<UUID, InteractionAttempt> rideAttempts = new HashMap<>();
    private final Map<UUID, InteractionAttempt> hangingAttackAttempts = new HashMap<>();

    public EntityProtectionListener(ProtectionDecisionService decisions) {
        this.decisions = decisions;
    }

    public void forget(ServerPlayer player) {
        interactionAttempts.remove(player.getUUID());
        rideAttempts.remove(player.getUUID());
        hangingAttackAttempts.remove(player.getUUID());
    }

    public void reset() {
        interactionAttempts.clear();
        rideAttempts.clear();
        hangingAttackAttempts.clear();
    }

    @SubscribeEvent
    public void onInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)
                || !decisions.active()) return;
        boolean allowed = interaction(player, event.getTarget());
        interactionAttempts.put(player.getUUID(), new InteractionAttempt(event.getTarget().getId(),
                event.getHand(), player.serverLevel().getGameTime(), allowed));
        if (!allowed) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)
                || !decisions.active()) return;
        InteractionAttempt previous = interactionAttempts.remove(player.getUUID());
        boolean allowed = previous != null && previous.matches(event.getTarget(), event.getHand(),
                player.serverLevel().getGameTime())
                ? previous.allowed() : interaction(player, event.getTarget());
        if (!allowed) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
        }
    }

    private boolean interaction(ServerPlayer player, Entity target) {
        ProtectionDecisionService.Context context = decisions.context(player, player.serverLevel());
        boolean rideable = target instanceof VehicleEntity
                || target instanceof Saddleable saddleable && saddleable.isSaddled();
        boolean allowed;
        if (target instanceof ItemFrame frame) {
            allowed = context.check(target, ProtectionDecisionEvent.Action.ENTITY_INTERACT,
                    frame.getItem().isEmpty() ? BUILD : ROTATE, "change that");
        } else if (target instanceof ChestBoat
                && (player.isSecondaryUseActive() || !target.getPassengers().isEmpty())
                || target instanceof AbstractMinecartContainer) {
            allowed = context.check(target, ProtectionDecisionEvent.Action.ENTITY_INTERACT, CHEST, "open that");
        } else if (isBuildingInteraction(target)) {
            allowed = context.check(target, ProtectionDecisionEvent.Action.ENTITY_INTERACT, BUILD, "change that");
        } else if (rideable) {
            allowed = context.check(target, ProtectionDecisionEvent.Action.RIDE, RIDE, "ride that");
            rideAttempts.put(player.getUUID(), new InteractionAttempt(target.getId(), null,
                    player.serverLevel().getGameTime(), allowed));
        } else if (isHostileOrAmbient(target) || target instanceof Player) {
            allowed = context.checkExplicit(target, ProtectionDecisionEvent.Action.ENTITY_INTERACT,
                    BUILD, "use that");
        } else {
            allowed = context.check(target, ProtectionDecisionEvent.Action.ENTITY_INTERACT,
                    INTERACT, "use that");
        }
        return allowed;
    }

    @SubscribeEvent
    public void onMount(EntityMountEvent event) {
        if (event.isCanceled() || !event.isMounting()
                || !(event.getEntityMounting() instanceof ServerPlayer player)
                || event.getEntityBeingMounted() == null || !decisions.active()) return;
        Entity target = event.getEntityBeingMounted();
        InteractionAttempt previous = rideAttempts.remove(player.getUUID());
        boolean allowed = previous != null && previous.matches(target, null,
                player.serverLevel().getGameTime())
                ? previous.allowed()
                : decisions.context(player, player.serverLevel()).check(target,
                        ProtectionDecisionEvent.Action.RIDE, RIDE, "ride that");
        if (!allowed) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onLivingDamage(LivingIncomingDamageEvent event) {
        if (event.isCanceled() || event.getEntity() instanceof ArmorStand
                || !(event.getEntity().level() instanceof ServerLevel) || !decisions.active()) return;
        DamageSource source = event.getSource();
        ServerPlayer attacker = EntityCauseResolver.player(source);
        if (attacker == null) return;
        if (!damage(attacker, event.getEntity(), source)) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onHangingAttack(AttackEntityEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)
                || !(event.getTarget() instanceof BlockAttachedEntity target) || !decisions.active()) return;
        boolean allowed = damage(player, target, player.damageSources().playerAttack(player));
        if (!allowed) {
            event.setCanceled(true);
        } else {
            hangingAttackAttempts.put(player.getUUID(), new InteractionAttempt(target.getId(), null,
                    player.serverLevel().getGameTime(), true));
        }
    }

    @SubscribeEvent
    public void onOtherDamage(EntityInvulnerabilityCheckEvent event) {
        Entity target = event.getEntity();
        if (event.isInvulnerable() || target instanceof Player
                || target instanceof net.minecraft.world.entity.LivingEntity && !(target instanceof ArmorStand)
                || !(target.level() instanceof ServerLevel) || !decisions.active()) return;
        ServerPlayer attacker = EntityCauseResolver.player(event.getSource());
        if (attacker == null) return;
        if (target instanceof BlockAttachedEntity && event.getSource().getDirectEntity() == attacker) {
            InteractionAttempt previous = hangingAttackAttempts.remove(attacker.getUUID());
            if (previous != null && previous.matches(target, null,
                    ((ServerLevel) target.level()).getGameTime())) return;
        }
        if (!damage(attacker, target, event.getSource())) {
            event.setInvulnerable(true);
        }
    }

    private boolean damage(ServerPlayer attacker, Entity target, DamageSource source) {
        ProtectionDecisionService.Context context = decisions.context(attacker, (ServerLevel) target.level());
        boolean firework = source.getDirectEntity() instanceof FireworkRocketEntity;
        if (target instanceof ServerPlayer defender && attacker != defender) {
            return context.checkPvp(defender, firework ? PVP_FIREWORK : PVP);
        } else if (target instanceof Player) {
            return context.checkExplicit(target, ProtectionDecisionEvent.Action.ENTITY_DAMAGE,
                    firework ? FIREWORK : BUILD, "damage that");
        } else if (target instanceof VehicleEntity) {
            return context.check(target, ProtectionDecisionEvent.Action.VEHICLE_DESTROY,
                    firework ? VEHICLE_DESTROY_FIREWORK : VEHICLE_DESTROY, "break vehicles");
        } else if (isHostileOrAmbient(target)) {
            // No implicit BUILD for hostile/ambient creatures in upstream Bukkit.
            return context.checkExplicit(target, ProtectionDecisionEvent.Action.ENTITY_DAMAGE,
                    firework ? FIREWORK : BUILD, "hit that");
        } else if (isBuildingInteraction(target)) {
            return context.check(target, ProtectionDecisionEvent.Action.ENTITY_DAMAGE,
                    firework ? FIREWORK : BUILD, "change that");
        } else if (isNonHostileCreature(target)) {
            return context.check(target, ProtectionDecisionEvent.Action.ENTITY_DAMAGE,
                    firework ? ANIMALS_FIREWORK : ANIMALS, "harm that");
        } else {
            return context.check(target, ProtectionDecisionEvent.Action.ENTITY_DAMAGE,
                    firework ? INTERACT_FIREWORK : INTERACT, "hit that");
        }
    }

    @SubscribeEvent
    public void onMinecartPlace(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)
                || !(event.getItemStack().getItem() instanceof MinecartItem)
                || !player.serverLevel().getBlockState(event.getPos()).is(BlockTags.RAILS)
                || !decisions.active()) return;
        if (!decisions.context(player, player.serverLevel()).check(event.getPos(),
                ProtectionDecisionEvent.Action.VEHICLE_PLACE, VEHICLE_PLACE, "place vehicles")) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onBoatPlace(PlayerInteractEvent.RightClickItem event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)
                || !(event.getItemStack().getItem() instanceof BoatItem) || !decisions.active()) return;
        ServerLevel level = player.serverLevel();
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getViewVector(1.0f).scale(player.blockInteractionRange()));
        BlockHitResult hit = level.clip(new ClipContext(start, end, ClipContext.Block.OUTLINE,
                ClipContext.Fluid.ANY, player));
        if (hit.getType() != HitResult.Type.BLOCK) return;
        BlockPos pos = BlockPos.containing(hit.getLocation());
        if (!decisions.context(player, level).check(pos,
                ProtectionDecisionEvent.Action.VEHICLE_PLACE, VEHICLE_PLACE, "place vehicles")) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
        }
    }

    private static boolean isBuildingInteraction(Entity entity) {
        return entity instanceof HangingEntity || entity instanceof ArmorStand
                || entity instanceof EndCrystal || entity instanceof Allay;
    }

    private static boolean isHostileOrAmbient(Entity entity) {
        MobCategory category = entity.getType().getCategory();
        return entity instanceof Enemy || category == MobCategory.MONSTER
                || category == MobCategory.AMBIENT;
    }

    private static boolean isNonHostileCreature(Entity entity) {
        return entity instanceof PathfinderMob && !isHostileOrAmbient(entity);
    }

    private record InteractionAttempt(int targetId, InteractionHand hand, long tick, boolean allowed) {
        private boolean matches(Entity target, InteractionHand usedHand, long now) {
            return targetId == target.getId() && hand == usedHand && tick == now;
        }
    }
}
