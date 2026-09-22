package de.kuro.worldguard.protection;

import com.sk89q.worldedit.world.entity.EntityType;
import com.sk89q.worldedit.world.entity.EntityTypes;
import com.sk89q.worldguard.config.WorldConfiguration;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.association.RegionAssociable;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.entity.vehicle.MinecartTNT;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.GrowingPlantHeadBlock;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.projectile.windcharge.BreezeWindCharge;
import net.minecraft.world.entity.projectile.windcharge.WindCharge;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityInvulnerabilityCheckEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.living.LivingDestroyBlockEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.PistonEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.CropGrowEvent;

import java.util.Iterator;
import java.util.List;

public final class EnvironmentalProtectionListener {
    private final EnvironmentalProtection protection;

    public EnvironmentalProtectionListener(EnvironmentalProtection protection) {
        this.protection = protection;
    }

    @SubscribeEvent
    public void onExplosionStart(ExplosionEvent.Start event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !protection.active()) return;
        WorldConfiguration cfg = protection.config(level);
        Entity source = event.getExplosion().getDirectSourceEntity();
        if (fullExplosionBlocked(cfg, source)) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !protection.active()) return;
        Explosion explosion = event.getExplosion();
        Entity source = explosion.getDirectSourceEntity();
        List<BlockPos> blocks = event.getAffectedBlocks();
        if (blocks.isEmpty()) return;
        EnvironmentalProtection.Context context = protection.context(level);
        WorldConfiguration cfg = context.config();
        if (blockExplosionBlocked(cfg, source)) {
            blocks.clear();
            return;
        }
        StateFlag flag = explosionFlag(source);
        // The cause is the explosive entity (or explosion origin for a block explosion),
        // so Core can apply overlap, parent and nonplayer domain association.
        BlockPos origin = source == null ? BlockPos.containing(explosion.center()) : source.blockPosition();
        RegionAssociable cause = cfg.useRegions ? context.source(origin) : null;
        boolean deniedByExplosionFlag = false;
        if (cfg.useRegions) {
            for (Iterator<BlockPos> it = blocks.iterator(); it.hasNext();) {
                BlockPos target = it.next();
                boolean flagAllowed = flag == Flags.TNT || context.state(target, flag);
                boolean buildAllowed = flag == Flags.TNT
                        ? context.build(target, cause, Flags.BLOCK_BREAK, Flags.TNT)
                        : context.build(target, cause, Flags.BLOCK_BREAK);
                if (!flagAllowed || !buildAllowed) {
                    it.remove();
                    deniedByExplosionFlag |= !flagAllowed;
                }
            }
        }
        if (deniedByExplosionFlag && cfg.explosionFlagCancellation) event.getAffectedEntities().clear();
    }

    private static StateFlag explosionFlag(Entity source) {
        if (source instanceof PrimedTnt || source instanceof MinecartTNT) return Flags.TNT;
        if (source instanceof Creeper) return Flags.CREEPER_EXPLOSION;
        if (source instanceof WitherSkull || source instanceof WitherBoss) return Flags.WITHER_DAMAGE;
        if (source instanceof BreezeWindCharge) return Flags.BREEZE_WIND_CHARGE;
        if (source instanceof WindCharge) return Flags.WIND_CHARGE_BURST;
        if (source instanceof AbstractHurtingProjectile) return Flags.GHAST_FIREBALL;
        if (source instanceof EnderDragon) return Flags.ENDERDRAGON_BLOCK_DAMAGE;
        return Flags.OTHER_EXPLOSION;
    }

    private static boolean fullExplosionBlocked(WorldConfiguration c, Entity source) {
        if (source instanceof PrimedTnt || source instanceof MinecartTNT) return c.blockTNTExplosions;
        if (source instanceof Creeper) return c.blockCreeperExplosions;
        if (source instanceof WitherSkull) return c.blockWitherSkullExplosions;
        if (source instanceof WitherBoss) return c.blockWitherExplosions;
        if (source instanceof BreezeWindCharge || source instanceof WindCharge) return c.blockWindChargeExplosions;
        if (source instanceof AbstractHurtingProjectile) return c.blockFireballExplosions;
        if (source instanceof EnderDragon) return false;
        return c.blockOtherExplosions;
    }

    private static boolean blockExplosionBlocked(WorldConfiguration c, Entity source) {
        if (source instanceof PrimedTnt || source instanceof MinecartTNT) return c.blockTNTBlockDamage;
        if (source instanceof Creeper) return c.blockCreeperBlockDamage;
        if (source instanceof WitherSkull) return c.blockWitherSkullBlockDamage;
        if (source instanceof WitherBoss) return c.blockWitherBlockDamage;
        if (source instanceof BreezeWindCharge || source instanceof WindCharge) return c.blockWindChargeExplosions;
        if (source instanceof AbstractHurtingProjectile) return c.blockFireballBlockDamage;
        if (source instanceof EnderDragon) return c.blockEnderDragonBlockDamage;
        return false;
    }

    @SubscribeEvent
    public void onPiston(PistonEvent.Pre event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level) || !protection.active()) return;
        if (!event.getPistonMoveType().isExtend && !level.getBlockState(event.getPos()).is(Blocks.STICKY_PISTON)) return;
        PistonStructureResolver resolver = event.getStructureHelper();
        if (resolver == null || !resolver.resolve()) return;
        EnvironmentalProtection.Context context = protection.context(level);
        if (!context.config().useRegions) return;
        RegionAssociable source = context.source(event.getPos());
        if (event.getPistonMoveType().isExtend
                && (!context.state(event.getFaceOffsetPos(), Flags.PISTONS)
                || !context.build(event.getFaceOffsetPos(), source, Flags.BLOCK_PLACE))) {
            event.setCanceled(true);
            return;
        }
        Direction movement = event.getPistonMoveType().isExtend
                ? event.getDirection() : event.getDirection().getOpposite();
        for (BlockPos pos : resolver.getToPush()) {
            if (!context.state(pos, Flags.PISTONS)
                    || !context.state(pos.relative(movement), Flags.PISTONS)
                    || !context.build(pos, source, Flags.BLOCK_BREAK)
                    || !context.build(pos.relative(movement), source, Flags.BLOCK_PLACE)) {
                event.setCanceled(true);
                return;
            }
        }
        for (BlockPos pos : resolver.getToDestroy()) {
            if (!context.state(pos, Flags.PISTONS) || !context.build(pos, source, Flags.BLOCK_BREAK)) {
                event.setCanceled(true);
                return;
            }
        }
    }

    @SubscribeEvent
    public void onSpawn(EntityJoinLevelEvent event) {
        if (event.isCanceled() || event.loadedFromDisk()
                || !(event.getLevel() instanceof ServerLevel level) || !protection.active()) return;
        Entity entity = event.getEntity();
        if (entity instanceof net.minecraft.world.entity.LightningBolt) {
            BlockPos pos = entity.blockPosition();
            WorldConfiguration cfg = protection.config(level);
            if (!cfg.disallowedLightningBlocks.isEmpty()) {
                var state = level.getBlockState(pos);
                if (state.isAir()) state = level.getBlockState(pos.below());
                if (cfg.disallowedLightningBlocks.contains(
                        BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString())) {
                    event.setCanceled(true);
                    return;
                }
            }
            if (cfg.useRegions && !protection.context(level).state(pos, Flags.LIGHTNING)) {
                event.setCanceled(true);
            }
            return;
        }
        if (!(entity instanceof Mob) || entity instanceof ArmorStand) return;
        WorldConfiguration cfg = protection.config(level);
        if (cfg.allowTamedSpawns && entity instanceof TamableAnimal tameable && tameable.isTame()) return;
        EntityType type = null;
        if (!cfg.blockCreatureSpawn.isEmpty()) {
            type = EntityTypes.get(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
            if (type != null && cfg.blockCreatureSpawn.contains(type)) {
                event.setCanceled(true);
                return;
            }
        }
        if (!cfg.useRegions || !com.sk89q.worldguard.WorldGuard.getInstance().getPlatform()
                .getGlobalStateManager().useRegionsCreatureSpawnEvent) return;
        EnvironmentalProtection.Context context = protection.context(level);
        ApplicableRegionSet set = context.query().getApplicableRegions(context.location(entity.blockPosition()));
        if (!set.testState(null, Flags.MOB_SPAWNING)) {
            event.setCanceled(true);
            return;
        }
        var denied = set.queryValue(null, Flags.DENY_SPAWN);
        if (denied != null) {
            if (type == null) type = EntityTypes.get(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
            if (type != null && denied.contains(type)) event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (event.isCanceled() || event.getSpawnType() != MobSpawnType.NATURAL
                || !(event.getEntity() instanceof Slime)
                || !(event.getEntity().level() instanceof ServerLevel level) || !protection.active()) return;
        if (event.getY() >= 60 && protection.config(level).blockGroundSlimes) {
            event.setSpawnCancelled(true);
        }
    }

    @SubscribeEvent
    public void onVehicleEntry(EntityMountEvent event) {
        if (event.isCanceled() || !event.isMounting() || event.getEntityMounting() instanceof net.minecraft.world.entity.player.Player
                || !(event.getEntityBeingMounted().level() instanceof ServerLevel level) || !protection.active()) return;
        if (protection.config(level).blockEntityVehicleEntry) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onMobDamage(LivingIncomingDamageEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)
                || !protection.active()) return;
        DamageSource damage = event.getSource();
        if (EntityCauseResolver.player(damage) != null) return;
        Entity attacker = damage.getEntity();
        if (attacker == null) attacker = damage.getDirectEntity();
        if (!(attacker instanceof Mob) && !damage.is(DamageTypes.WITHER)
                || attacker instanceof TamableAnimal) return;
        WorldConfiguration cfg = protection.config(player.serverLevel());
        if (cfg.disableMobDamage) { event.setCanceled(true); return; }
        if (cfg.useRegions) {
            EnvironmentalProtection.Context context = protection.context(player.serverLevel());
            if (!context.query().getApplicableRegions(context.location(player.blockPosition()))
                    .testState(protection.player(player), Flags.MOB_DAMAGE)) event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onMobGrief(EntityMobGriefingEvent event) {
        if (!event.canGrief() || !(event.getEntity().level() instanceof ServerLevel level)
                || !protection.active()) return;
        Entity entity = event.getEntity();
        WorldConfiguration cfg = protection.config(level);
        if (entity instanceof EnderMan) {
            if (cfg.disableEndermanGriefing) { event.setCanGrief(false); return; }
        } else if (entity instanceof SnowGolem) {
            if (cfg.disableSnowmanTrails) { event.setCanGrief(false); return; }
        }
    }

    @SubscribeEvent
    public void onCreatureTrample(BlockEvent.FarmlandTrampleEvent event) {
        if (event.isCanceled() || event.getEntity() instanceof ServerPlayer
                || !(event.getLevel() instanceof ServerLevel level) || !protection.active()) return;
        if (protection.config(level).disableCreatureCropTrampling) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onMobBreak(LivingDestroyBlockEvent event) {
        if (event.isCanceled() || !(event.getEntity().level() instanceof ServerLevel level)
                || !protection.active()) return;
        Entity entity = event.getEntity();
        WorldConfiguration cfg = protection.config(level);
        StateFlag flag = null;
        if (entity instanceof WitherBoss) {
            if (cfg.blockWitherExplosions || cfg.blockWitherBlockDamage) { event.setCanceled(true); return; }
            flag = Flags.WITHER_DAMAGE;
        } else if (entity instanceof EnderDragon) {
            if (cfg.blockEnderDragonBlockDamage) { event.setCanceled(true); return; }
            flag = Flags.ENDERDRAGON_BLOCK_DAMAGE;
        } else if (entity instanceof Zombie && event.getState().is(BlockTags.DOORS)
                && cfg.blockZombieDoorDestruction) {
            event.setCanceled(true);
            return;
        }
        if (flag != null && !protection.context(level).state(event.getPos(), flag)) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onDecorationDamage(EntityInvulnerabilityCheckEvent event) {
        if (event.isInvulnerable() || !(event.getEntity().level() instanceof ServerLevel level)
                || !protection.active() || EntityCauseResolver.player(event.getSource()) != null) return;
        WorldConfiguration cfg = protection.config(level);
        StateFlag flag;
        if (event.getEntity() instanceof Painting) {
            if (cfg.blockEntityPaintingDestroy) { event.setInvulnerable(true); return; }
            flag = Flags.ENTITY_PAINTING_DESTROY;
        } else if (event.getEntity() instanceof ItemFrame) {
            if (cfg.blockEntityItemFrameDestroy) { event.setInvulnerable(true); return; }
            flag = Flags.ENTITY_ITEM_FRAME_DESTROY;
        } else if (event.getEntity() instanceof ArmorStand) {
            if (cfg.blockEntityArmorStandDestroy) event.setInvulnerable(true);
            return;
        } else return;
        if (!protection.context(level).state(event.getEntity().blockPosition(), flag)) event.setInvulnerable(true);
    }

    @SubscribeEvent
    public void onCropGrow(CropGrowEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !protection.active()) return;
        WorldConfiguration cfg = protection.config(level);
        boolean vine = event.getState().getBlock() instanceof GrowingPlantHeadBlock;
        StateFlag flag = vine ? Flags.VINE_GROWTH : Flags.CROP_GROWTH;
        if ((vine ? cfg.disableVineGrowth : cfg.disableCropGrowth)
                || cfg.useRegions && !protection.context(level).state(event.getPos(), flag)) {
            event.setResult(CropGrowEvent.Pre.Result.DO_NOT_GROW);
        }
    }

    @SubscribeEvent
    public void onFluidPlace(BlockEvent.FluidPlaceBlockEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)
                || !protection.active()) return;
        if (event.getNewState().is(BlockTags.FIRE)
                && level.getFluidState(event.getLiquidPos()).is(net.minecraft.tags.FluidTags.LAVA)
                && !EnvironmentalHooks.fire(level, event.getPos(), true)) {
            event.setCanceled(true);
        } else if (!event.getLiquidPos().equals(event.getPos())
                && !event.getNewState().is(BlockTags.FIRE)
                && !EnvironmentalHooks.fluid(level, event.getLiquidPos(), event.getPos(),
                event.getOriginalState(), level.getFluidState(event.getLiquidPos()))) {
            event.setCanceled(true);
        }
    }
}
