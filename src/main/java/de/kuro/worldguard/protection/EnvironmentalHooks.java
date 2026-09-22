package de.kuro.worldguard.protection;

import com.sk89q.worldguard.config.WorldConfiguration;
import com.sk89q.worldguard.protection.association.RegionAssociable;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import de.kuro.worldguard.api.ProtectionDecisionEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

public final class EnvironmentalHooks {
    private static EnvironmentalProtection protection;
    private static ProtectionDecisionService decisions;
    private static final StateFlag[] FROST = {Flags.FROSTED_ICE_FORM};
    private static final StateFlag[] POTION = {Flags.POTION_SPLASH};
    private static final StateFlag[] DRIPLEAF = {Flags.USE_DRIPLEAF};
    private static final StateFlag[] TRAMPLE = {Flags.TRAMPLE_BLOCKS};
    private static final StateFlag[] PHYSICAL_USE = {Flags.INTERACT, Flags.USE};

    private EnvironmentalHooks() {}

    public static void install(EnvironmentalProtection service, ProtectionDecisionService playerDecisions) {
        protection = service;
        decisions = playerDecisions;
    }
    public static void uninstall() { protection = null; decisions = null; }

    public static boolean fluid(LevelAccessor access, BlockPos source, BlockPos target,
                                BlockState oldState, FluidState fluid) {
        EnvironmentalProtection service = protection;
        if (service == null || !service.active() || !(access instanceof ServerLevel level)) return true;
        WorldConfiguration config = service.config(level);
        // This is reached for every fluid edge: no query or wrapper allocation when off.
        if ((!config.useRegions || !config.highFreqFlags && !config.checkLiquidFlow)
                && config.preventWaterDamage.isEmpty() && config.allowedLavaSpreadOver.isEmpty()) return true;
        if (oldState.getFluidState().is(fluid.getType())) return true;
        boolean water = fluid.is(FluidTags.WATER);
        boolean lava = fluid.is(FluidTags.LAVA);
        if (!water && !lava) return true;
        if (water && config.preventWaterDamage.contains(id(oldState))) return false;
        if (lava && !config.allowedLavaSpreadOver.isEmpty()
                && !config.allowedLavaSpreadOver.contains(id(level.getBlockState(target.below())))) return false;
        if (!config.useRegions || !config.highFreqFlags && !config.checkLiquidFlow) return true;
        EnvironmentalProtection.Context context = service.context(level);
        if (config.highFreqFlags && !context.state(source, water ? Flags.WATER_FLOW : Flags.LAVA_FLOW)) return false;
        if (config.checkLiquidFlow) {
            RegionAssociable cause = context.source(source);
            if (!context.build(target, cause, Flags.BLOCK_PLACE)) return false;
        }
        return true;
    }

    public static boolean fire(ServerLevel level, BlockPos target, boolean lava) {
        EnvironmentalProtection service = protection;
        if (service == null || !service.active()) return true;
        WorldConfiguration config = service.config(level);
        if (config.fireSpreadDisableToggle
                || (lava ? config.preventLavaFire : config.disableFireSpread)) return false;
        if (!lava && !config.disableFireSpreadBlocks.isEmpty()
                && (config.disableFireSpreadBlocks.contains(id(level.getBlockState(target.below())))
                || config.disableFireSpreadBlocks.contains(id(level.getBlockState(target.north())))
                || config.disableFireSpreadBlocks.contains(id(level.getBlockState(target.south())))
                || config.disableFireSpreadBlocks.contains(id(level.getBlockState(target.east())))
                || config.disableFireSpreadBlocks.contains(id(level.getBlockState(target.west()))))) return false;
        if (!config.highFreqFlags || !config.useRegions) return true;
        return service.context(level).state(target, lava ? Flags.LAVA_FIRE : Flags.FIRE_SPREAD);
    }

    public static boolean fireBurn(ServerLevel level, BlockPos target) {
        EnvironmentalProtection service = protection;
        if (service == null || !service.active()) return true;
        WorldConfiguration cfg = service.config(level);
        if (cfg.fireSpreadDisableToggle || cfg.disableFireSpread
                || cfg.disableFireSpreadBlocks.contains(id(level.getBlockState(target)))) return false;
        return !cfg.highFreqFlags || !cfg.useRegions
                || service.context(level).state(target, Flags.FIRE_SPREAD);
    }

    private static String id(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    public static boolean natural(ServerLevel level, BlockPos target,
                                  com.sk89q.worldguard.protection.flags.StateFlag flag,
                                  boolean globalDisabled) {
        if (globalDisabled) return false;
        EnvironmentalProtection service = protection;
        return service == null || !service.active() || service.context(level).state(target, flag);
    }

    public static EnvironmentalProtection service() { return protection; }

    /** Preserve the player's own death packet while suppressing public death broadcasts. */
    public static boolean showDeathMessage(ServerPlayer player) {
        EnvironmentalProtection service = protection;
        return service == null || !service.active()
                || !service.config(player.serverLevel()).disableDeathMessages;
    }

    public static boolean potionSplash(ThrownPotion potion, LivingEntity target) {
        EnvironmentalProtection service = protection;
        if (service == null || !service.active() || !(potion.level() instanceof ServerLevel level)) return true;
        if (potion.getOwner() instanceof ServerPlayer player && decisions != null) {
            return decisions.context(player, level).check(target,
                    ProtectionDecisionEvent.Action.ENTITY_INTERACT, POTION, "splash that");
        }
        EnvironmentalProtection.Context context = service.context(level);
        return context.build(target.blockPosition(), context.source(potion.blockPosition()), Flags.POTION_SPLASH);
    }

    public static boolean dripleaf(ServerLevel level, BlockPos pos, Entity actor) {
        EnvironmentalProtection service = protection;
        if (service == null || !service.active()) return true;
        if (actor instanceof ServerPlayer player && decisions != null) {
            return decisions.context(player, level).check(pos,
                    ProtectionDecisionEvent.Action.USE, DRIPLEAF, "use that dripleaf");
        }
        EnvironmentalProtection.Context context = service.context(level);
        return context.build(pos, context.source(actor.blockPosition()), Flags.USE_DRIPLEAF);
    }

    public static boolean turtleEggTrample(ServerLevel level, BlockPos pos, Entity actor) {
        EnvironmentalProtection service = protection;
        if (service == null || !service.active()) return true;
        WorldConfiguration config = service.config(level);
        if (actor instanceof ServerPlayer player) {
            if (config.disablePlayerTurtleEggTrampling) return false;
            return !config.useRegions || decisions == null || decisions.context(player, level).check(pos,
                    ProtectionDecisionEvent.Action.TRAMPLE, TRAMPLE, "trample that egg");
        }
        if (config.disableCreatureTurtleEggTrampling) return false;
        EnvironmentalProtection.Context context = service.context(level);
        return !config.useRegions || context.build(pos, context.source(actor.blockPosition()), Flags.TRAMPLE_BLOCKS);
    }

    public static boolean physicalUse(ServerLevel level, BlockPos pos, Entity actor) {
        if (!(actor instanceof ServerPlayer player) || decisions == null || !decisions.active()) return true;
        return decisions.context(player, level).check(pos,
                ProtectionDecisionEvent.Action.USE, PHYSICAL_USE, "activate that");
    }

    public static boolean mobBlock(ServerLevel level, Entity actor, BlockPos target,
                                   StateFlag flag, boolean place) {
        EnvironmentalProtection service = protection;
        if (service == null || !service.active()) return true;
        EnvironmentalProtection.Context context = service.context(level);
        WorldConfiguration cfg = context.config();
        if (actor instanceof net.minecraft.world.entity.monster.EnderMan && cfg.disableEndermanGriefing
                || actor instanceof net.minecraft.world.entity.animal.SnowGolem && cfg.disableSnowmanTrails) return false;
        RegionAssociable cause = context.source(actor.blockPosition());
        return context.state(target, flag)
                && context.build(target, cause, place ? Flags.BLOCK_PLACE : Flags.BLOCK_BREAK);
    }

    public static boolean enchantmentPlace(ServerLevel level, BlockPos target,
                                           BlockState state, Entity actor) {
        if (!state.is(Blocks.FROSTED_ICE)) return true;
        EnvironmentalProtection service = protection;
        if (service == null || !service.active()) return true;
        if (actor instanceof ServerPlayer player && decisions != null) {
            return decisions.context(player, level).check(target,
                    ProtectionDecisionEvent.Action.PLACE, FROST, "freeze water");
        }
        EnvironmentalProtection.Context context = service.context(level);
        return context.build(target, context.source(actor.blockPosition()), FROST);
    }
}
