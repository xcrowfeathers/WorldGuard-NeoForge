package de.kuro.worldguard.protection;

import com.sk89q.worldedit.neoforge.NeoForgeAdapter;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.gamemode.GameMode;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.commands.CommandUtils;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import com.sk89q.worldguard.session.Session;
import com.sk89q.worldguard.util.command.CommandFilter;
import de.kuro.worldguard.api.ProtectionDecisionEvent;
import de.kuro.worldguard.platform.NeoForgeLocalPlayer;
import de.kuro.worldguard.platform.NaturalPlayerState;
import de.kuro.worldguard.platform.NeoForgeWorldGuardPlatform;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.event.entity.player.PlayerRespawnPositionEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

import java.util.Set;

/** Player state checks that are not themselves Core movement handlers. */
public final class SessionPlayerFlagsListener {
    private static final com.sk89q.worldguard.protection.flags.StateFlag[] PICKUP = {Flags.ITEM_PICKUP};
    private static final com.sk89q.worldguard.protection.flags.StateFlag[] DROP = {Flags.ITEM_DROP};
    private static final com.sk89q.worldguard.protection.flags.StateFlag[] XP = {Flags.EXP_DROPS};
    private static SessionPlayerFlagsListener installed;

    private final NeoForgeWorldGuardPlatform platform;
    private final ProtectionDecisionService decisions;
    private final SessionMovement movement;

    public SessionPlayerFlagsListener(NeoForgeWorldGuardPlatform platform,
                                      ProtectionDecisionService decisions, SessionMovement movement) {
        this.platform = platform;
        this.decisions = decisions;
        this.movement = movement;
        installed = this;
    }

    private boolean active() { return platform.getRegionContainer() != null; }

    private boolean state(ServerPlayer player, StateFlag flag) {
        LocalPlayer local = platform.adapt(player);
        return !platform.getGlobalStateManager().get(local.getWorld()).useRegions
                || platform.getRegionContainer().createQuery().testState(local.getLocation(), local, flag);
    }

    public static boolean naturalHealth(Player player) {
        if (!(player instanceof ServerPlayer server) || installed == null || !installed.active()) return true;
        LocalPlayer local = installed.platform.adapt(server);
        if (installed.platform.getGlobalStateManager().get(local.getWorld()).disableHealthRegain) return false;
        NaturalPlayerState state = installed.platform.getSessionManager().get(local)
                .getHandler(NaturalPlayerState.class);
        return state == null || state.healthRegen();
    }

    public static boolean naturalHunger(Player player) {
        if (!(player instanceof ServerPlayer server) || installed == null || !installed.active()) return true;
        LocalPlayer local = installed.platform.adapt(server);
        Session session = installed.platform.getSessionManager().get(local);
        NaturalPlayerState state = session.getHandler(NaturalPlayerState.class);
        return !session.isInvincible(local) && (state == null || state.hungerDrain());
    }

    @SubscribeEvent
    public void onTeleport(EntityTeleportEvent event) {
        if (!movement.teleport(event)) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onRespawnPosition(PlayerRespawnPositionEvent event) {
        if (!active() || !(event.getEntity() instanceof ServerPlayer player)) return;
        LocalPlayer local = platform.adapt(player);
        if (!platform.getGlobalStateManager().get(local.getWorld()).useRegions) return;
        ApplicableRegionSet set = platform.getRegionContainer().createQuery().getApplicableRegions(local.getLocation());
        Location spawn = set.queryValue(local, Flags.SPAWN_LOC);
        if (spawn == null) return;
        ServerLevel destination = NeoForgeAdapter.adapt((World) spawn.getExtent());
        DimensionTransition original = event.getDimensionTransition();
        event.setDimensionTransition(new DimensionTransition(destination,
                new Vec3(spawn.getX(), spawn.getY(), spawn.getZ()), original.speed(),
                spawn.getYaw(), spawn.getPitch(), original.missingRespawnBlock(),
                original.postDimensionTransition()));
    }

    @SubscribeEvent
    public void onGameMode(PlayerEvent.PlayerChangeGameModeEvent event) {
        if (!active() || !(event.getEntity() instanceof ServerPlayer player)) return;
        NeoForgeLocalPlayer local = platform.adapt(player);
        if (local.isApplyingRegionGameMode() || !platform.getGlobalStateManager().get(local.getWorld()).useRegions
                || platform.getSessionManager().hasBypass(local, local.getWorld())) return;
        Session session = platform.getSessionManager().getIfPresent(local);
        if (session == null) return;
        var handler = session.getHandler(com.sk89q.worldguard.session.handler.GameModeFlag.class);
        if (handler == null || handler.getOriginalGameMode() == null) return;
        GameMode required = platform.getRegionContainer().createQuery()
                .queryValue(local.getLocation(), local, Flags.GAME_MODE);
        if (required != null && !required.id().equals(event.getNewGameMode().getName())) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onDamage(LivingIncomingDamageEvent event) {
        if (event.isCanceled() || !active() || !(event.getEntity() instanceof ServerPlayer player)) return;
        LocalPlayer local = platform.adapt(player);
        Session session = platform.getSessionManager().get(local);
        if (session.isInvincible(local)) {
            player.setRemainingFireTicks(0);
            event.setCanceled(true);
        } else if (event.getSource().is(DamageTypes.FALL)
                && platform.getGlobalStateManager().get(local.getWorld()).disableFallDamage) {
            event.setCanceled(true);
        } else if ((event.getSource().is(DamageTypes.FALL) || event.getSource().is(DamageTypes.FLY_INTO_WALL))
                && !state(player, Flags.FALL_DAMAGE)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onXpOrbSpawn(EntityJoinLevelEvent event) {
        if (active() && event.getEntity() instanceof ExperienceOrb
                && event.getLevel() instanceof ServerLevel level
                && platform.getGlobalStateManager().get(platform.adaptWorld(level)).disableExpDrops) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onPickup(ItemEntityPickupEvent.Pre event) {
        if (!active() || !(event.getPlayer() instanceof ServerPlayer player)
                || event.canPickup() == TriState.FALSE) return;
        if (!decisions.context(player, player.serverLevel()).check(event.getItemEntity(),
                ProtectionDecisionEvent.Action.ITEM_PICKUP, PICKUP, "pick up that")) {
            event.setCanPickup(TriState.FALSE);
        }
    }

    @SubscribeEvent
    public void onXpPickup(PlayerXpEvent.PickupXp event) {
        if (!active() || event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)) return;
        if (!decisions.context(player, player.serverLevel()).check(event.getOrb(),
                ProtectionDecisionEvent.Action.ITEM_PICKUP, PICKUP, "pick up XP")) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onDrop(ItemTossEvent event) {
        if (!active() || event.isCanceled() || !(event.getPlayer() instanceof ServerPlayer player)) return;
        if (!decisions.context(player, player.serverLevel()).check(event.getEntity(),
                ProtectionDecisionEvent.Action.ITEM_DROP, DROP, "drop that")) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onBlockXp(BlockDropsEvent event) {
        if (!active() || event.getDroppedExperience() <= 0
                || !(event.getBreaker() instanceof ServerPlayer player)) return;
        if (!decisions.context(player, player.serverLevel()).check(event.getPos(),
                ProtectionDecisionEvent.Action.EXP_DROP, XP, "drop XP")) event.setDroppedExperience(0);
    }

    @SubscribeEvent
    public void onMobXp(LivingExperienceDropEvent event) {
        if (!active() || event.isCanceled() || event.getDroppedExperience() <= 0
                || !(event.getAttackingPlayer() instanceof ServerPlayer player)) return;
        if (!decisions.context(player, player.serverLevel()).check(event.getEntity(),
                ProtectionDecisionEvent.Action.EXP_DROP, XP, "drop XP")) event.setDroppedExperience(0);
    }

    @SubscribeEvent
    public void onCommand(CommandEvent event) {
        if (!active() || event.isCanceled()) return;
        ServerPlayer player = event.getParseResults().getContext().getSource().getPlayer();
        if (player == null) return;
        LocalPlayer local = platform.adapt(player);
        if (!platform.getGlobalStateManager().get(local.getWorld()).useRegions
                || platform.getSessionManager().hasBypass(local, local.getWorld())) return;
        ApplicableRegionSet set = platform.getRegionContainer().createQuery().getApplicableRegions(local.getLocation());
        Set<String> allowed = set.queryValue(local, Flags.ALLOWED_CMDS);
        Set<String> blocked = set.queryValue(local, Flags.BLOCKED_CMDS);
        if (allowed == null && blocked == null) return;
        String command = event.getParseResults().getReader().getString().trim();
        if (!command.startsWith("/")) command = "/" + command;
        if (!new CommandFilter(allowed, blocked).apply(command)) {
            event.setCanceled(true);
            deny(local, set.queryValue(local, Flags.DENY_MESSAGE), "use that command");
        }
    }

    @SubscribeEvent
    public void onChat(ServerChatEvent event) {
        if (!active() || event.isCanceled()) return;
        ServerPlayer player = event.getPlayer();
        LocalPlayer local = platform.adapt(player);
        if (!platform.getGlobalStateManager().get(local.getWorld()).useRegions) return;
        RegionQuery query = platform.getRegionContainer().createQuery();
        ApplicableRegionSet set = query.getApplicableRegions(local.getLocation());
        if (!set.testState(local, Flags.SEND_CHAT)) {
            event.setCanceled(true);
            deny(local, set.queryValue(local, Flags.DENY_MESSAGE), "chat");
        }
    }

    public static boolean receiveChat(ServerPlayer recipient) {
        return installed == null || !installed.active() || installed.state(recipient, Flags.RECEIVE_CHAT);
    }

    private static void deny(LocalPlayer local, String message, String action) {
        if (message == null || message.isBlank()) {
            local.printRaw("You cannot " + action + " here.");
        } else {
            local.printRaw(CommandUtils.replaceColorMacros(message.replace("%what%", action)));
        }
    }
}
