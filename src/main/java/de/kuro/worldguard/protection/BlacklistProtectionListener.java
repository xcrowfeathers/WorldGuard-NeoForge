package de.kuro.worldguard.protection;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.block.BlockTypes;
import com.sk89q.worldedit.world.item.ItemType;
import com.sk89q.worldedit.world.item.ItemTypes;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.blacklist.Blacklist;
import com.sk89q.worldguard.blacklist.event.BlockBreakBlacklistEvent;
import com.sk89q.worldguard.blacklist.event.BlockDispenseBlacklistEvent;
import com.sk89q.worldguard.blacklist.event.BlockInteractBlacklistEvent;
import com.sk89q.worldguard.blacklist.event.BlockPlaceBlacklistEvent;
import com.sk89q.worldguard.blacklist.event.ItemAcquireBlacklistEvent;
import com.sk89q.worldguard.blacklist.event.ItemDestroyWithBlacklistEvent;
import com.sk89q.worldguard.blacklist.event.ItemDropBlacklistEvent;
import com.sk89q.worldguard.blacklist.event.ItemUseBlacklistEvent;
import com.sk89q.worldguard.blacklist.target.BlockTarget;
import com.sk89q.worldguard.blacklist.target.ItemTarget;
import de.kuro.worldguard.platform.NeoForgeWorldGuardPlatform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/** NeoForge event mapping for Core's compiled WorldGuard blacklist rules. */
public final class BlacklistProtectionListener {
    private static BlacklistProtectionListener installed;
    private final NeoForgeWorldGuardPlatform platform;
    private final EntityInteractionPair entityInteractions = new EntityInteractionPair();

    public BlacklistProtectionListener(NeoForgeWorldGuardPlatform platform) {
        this.platform = platform;
        installed = this;
    }

    public void forget(ServerPlayer player) { entityInteractions.forget(player); }
    public void clear() { entityInteractions.clear(); }

    private Blacklist rules(ServerLevel level) {
        return platform.getRegionContainer() == null || !platform.hasBlacklistRules() ? null
                : platform.getGlobalStateManager().get(platform.adaptWorld(level)).getBlacklist();
    }

    private static BlockVector3 at(BlockPos pos) {
        return BlockVector3.at(pos.getX(), pos.getY(), pos.getZ());
    }

    private static BlockTarget block(BlockState state) {
        BlockType type = BlockTypes.get(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        return type == null ? null : new BlockTarget(type);
    }

    private static ItemTarget item(ItemStack stack) {
        if (stack.isEmpty()) return null;
        ItemType type = ItemTypes.get(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        return type == null ? null : new ItemTarget(type);
    }

    @SubscribeEvent
    public void onBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled() || !(event.getPlayer() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level)) return;
        Blacklist rules = rules(level);
        if (rules == null) return;
        LocalPlayer actor = platform.adapt(player);
        BlockTarget target = block(event.getState());
        if (target != null && !rules.check(new BlockBreakBlacklistEvent(actor, at(event.getPos()), target), false, false)) {
            event.setCanceled(true);
            return;
        }
        ItemTarget tool = item(player.getMainHandItem());
        if (tool != null && !rules.check(new ItemDestroyWithBlacklistEvent(actor, at(event.getPos()), tool), false, false)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level)) return;
        Blacklist rules = rules(level);
        if (rules == null) return;
        LocalPlayer actor = platform.adapt(player);
        if (event instanceof BlockEvent.EntityMultiPlaceEvent many) {
            for (BlockSnapshot snapshot : many.getReplacedBlockSnapshots()) {
                if (!place(rules, actor, snapshot)) {
                    event.setCanceled(true);
                    return;
                }
            }
        } else if (!place(rules, actor, event.getBlockSnapshot())) {
            event.setCanceled(true);
        }
    }

    private static boolean place(Blacklist rules, LocalPlayer actor, BlockSnapshot snapshot) {
        BlockTarget target = block(snapshot.getCurrentState());
        return target == null || rules.check(new BlockPlaceBlacklistEvent(actor, at(snapshot.getPos()), target), false, false);
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)) return;
        Blacklist rules = rules(player.serverLevel());
        if (rules == null) return;
        LocalPlayer actor = platform.adapt(player);
        BlockPos pos = event.getPos();
        BlockTarget target = block(player.serverLevel().getBlockState(pos));
        if (target != null && !rules.check(new BlockInteractBlacklistEvent(actor, at(pos), target), false, false)) {
            deny(event);
            return;
        }
        ItemTarget held = item(event.getItemStack());
        if (held != null && !rules.check(new ItemUseBlacklistEvent(actor, at(pos), held), false, false)) {
            deny(event);
        }
    }

    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)) return;
        Blacklist rules = rules(player.serverLevel());
        if (rules == null) return;
        ItemTarget target = item(event.getItemStack());
        if (target != null && !rules.check(new ItemUseBlacklistEvent(platform.adapt(player),
                at(player.blockPosition()), target), false, false)) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)) return;
        if (entityInteractions.alreadyChecked(player, event.getTarget(), event.getHand())) return;
        if (!checkEntityItemUse(player, event.getItemStack(), event.getTarget().blockPosition())) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)) return;
        if (!checkEntityItemUse(player, event.getItemStack(), event.getTarget().blockPosition())) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
        } else {
            entityInteractions.record(player, event.getTarget(), event.getHand());
        }
    }

    private boolean checkEntityItemUse(ServerPlayer player, ItemStack stack, BlockPos pos) {
        Blacklist rules = rules(player.serverLevel());
        if (rules == null) return true;
        ItemTarget target = item(stack);
        return target == null || rules.check(new ItemUseBlacklistEvent(platform.adapt(player),
                at(pos), target), false, false);
    }

    @SubscribeEvent
    public void onDrop(ItemTossEvent event) {
        if (event.isCanceled() || !(event.getPlayer() instanceof ServerPlayer player)) return;
        Blacklist rules = rules(player.serverLevel());
        if (rules == null) return;
        ItemTarget target = item(event.getEntity().getItem());
        if (target != null && !rules.check(new ItemDropBlacklistEvent(platform.adapt(player),
                at(event.getEntity().blockPosition()), target), false, false)) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onPickup(ItemEntityPickupEvent.Pre event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || event.canPickup() == TriState.FALSE) return;
        Blacklist rules = rules(player.serverLevel());
        if (rules == null) return;
        ItemTarget target = item(event.getItemEntity().getItem());
        if (target != null && !rules.check(new ItemAcquireBlacklistEvent(platform.adapt(player),
                at(event.getItemEntity().blockPosition()), target), false, false)) {
            event.setCanPickup(TriState.FALSE);
        }
    }

    private static void deny(PlayerInteractEvent.RightClickBlock event) {
        event.setCancellationResult(InteractionResult.FAIL);
        event.setCanceled(true);
    }

    /** Called only at the dispenser's item dispatch point, before its behavior runs. */
    public static boolean allowDispense(ServerLevel level, BlockPos pos, ItemStack stack) {
        BlacklistProtectionListener listener = installed;
        if (listener == null) return true;
        Blacklist rules = listener.rules(level);
        if (rules == null) return true;
        ItemTarget target = item(stack);
        return target == null || rules.check(new BlockDispenseBlacklistEvent(null, at(pos), target), false, false);
    }
}
