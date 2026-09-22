package de.kuro.worldguard.protection;

import com.sk89q.worldedit.neoforge.NeoForgeAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import de.kuro.worldguard.api.ProtectionDecisionEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.item.MinecartItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BigDripleafBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.entity.player.BonemealEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.UseItemOnBlockEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

public final class PlayerProtectionListener {
    private static final StateFlag[] BREAK = {Flags.BLOCK_BREAK};
    private static final StateFlag[] PLACE = {Flags.BLOCK_PLACE};
    private static final StateFlag[] INTERACT = {Flags.INTERACT};
    private static final StateFlag[] USE = {Flags.INTERACT, Flags.USE};
    private static final StateFlag[] INVENTORY = {Flags.CHEST_ACCESS};
    private static final StateFlag[] ANVIL = {Flags.USE_ANVIL};
    private static final StateFlag[] DRIPLEAF = {Flags.USE_DRIPLEAF};
    private static final StateFlag[] BED = {Flags.INTERACT, Flags.SLEEP};
    private static final StateFlag[] ANCHOR = {Flags.INTERACT, Flags.RESPAWN_ANCHORS};
    private static final StateFlag[] IGNITE = {Flags.BLOCK_PLACE, Flags.LIGHTER};
    private static final StateFlag[] TNT = {Flags.INTERACT, Flags.TNT};
    private static final StateFlag[] TRAMPLE_BREAK = {Flags.BLOCK_BREAK, Flags.TRAMPLE_BLOCKS};
    private static final StateFlag[] TRAMPLE_PLACE = {Flags.BLOCK_PLACE, Flags.TRAMPLE_BLOCKS};
    private static final StateFlag[] BUILD_ONLY = {};
    private final ProtectionDecisionService decisions;

    public PlayerProtectionListener(ProtectionDecisionService decisions) {
        this.decisions = decisions;
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) decisions.forget(player);
    }

    public void reset() {
        decisions.reset();
    }

    @SubscribeEvent
    public void onBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled() || !(event.getPlayer() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level) || !decisions.active()) return;
        ProtectionDecisionService.Context context = decisions.context(player, level);
        if (!context.check(event.getPos(), ProtectionDecisionEvent.Action.BREAK, BREAK, "break that block")) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level) || !decisions.active()) return;
        ProtectionDecisionService.Context context = decisions.context(player, level);
        if (event instanceof BlockEvent.EntityMultiPlaceEvent multiple) {
            for (BlockSnapshot snapshot : multiple.getReplacedBlockSnapshots()) {
                if (!checkPlacement(context, snapshot)) {
                    event.setCanceled(true); // NeoForge rolls the complete snapshot set back.
                    return;
                }
            }
        } else if (!checkPlacement(context, event.getBlockSnapshot())) {
            event.setCanceled(true);
        }
    }

    private boolean checkPlacement(ProtectionDecisionService.Context context, BlockSnapshot snapshot) {
        BlockPos pos = snapshot.getPos();
        BlockState before = snapshot.getState();
        BlockState after = snapshot.getCurrentState();
        if (!before.isAir() && before.getBlock() != after.getBlock()
                && !context.check(pos, ProtectionDecisionEvent.Action.BREAK, BREAK, "replace that block")) {
            return false;
        }
        if (after.getBlock() instanceof BaseFireBlock) {
            return context.check(pos, ProtectionDecisionEvent.Action.IGNITE, IGNITE, "place fire");
        }
        return context.check(pos, ProtectionDecisionEvent.Action.PLACE, PLACE, "place that block");
    }

    @SubscribeEvent
    public void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player) || !decisions.active()) return;
        ServerLevel level = player.serverLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        ItemStack item = event.getItemStack();
        ProtectionDecisionService.Context context = null;

        // Inventory access belongs to the block being opened, including both halves of a chest.
        if (isInventory(level, pos, state)) {
            context = decisions.context(player, level);
            if (!context.check(pos, ProtectionDecisionEvent.Action.INVENTORY, INVENTORY, "open that")) {
                deny(event);
                return;
            }
            if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
                BlockPos other = pos.relative(ChestBlock.getConnectedDirection(state));
                if (!context.check(other, ProtectionDecisionEvent.Action.INVENTORY, INVENTORY, "open that")) {
                    deny(event);
                    return;
                }
            }
            return;
        } else if (isIgnition(item) && WorldGuard.getInstance().getPlatform().getGlobalStateManager()
                .get(NeoForgeAdapter.adapt(level)).blockLighter) {
            deny(event);
            return;
        } else if (state.is(Blocks.TNT) && isIgnition(item)) {
            context = decisions.context(player, level);
            if (!context.check(pos, ProtectionDecisionEvent.Action.USE, TNT, "use explosives")) {
                deny(event);
            }
            return;
        } else if (isIgnition(item)) {
            context = decisions.context(player, level);
            BlockPos target = state.canBeReplaced() ? pos : pos.relative(event.getFace());
            if (!context.check(target, ProtectionDecisionEvent.Action.IGNITE, IGNITE, "place fire")) {
                deny(event);
                return;
            }
        } else if (isBucket(item)) {
            context = decisions.context(player, level);
            BlockPos target = item.is(Items.BUCKET) || isFluidContainerTarget(state) || state.canBeReplaced()
                    ? pos : pos.relative(event.getFace());
            StateFlag[] flags = item.is(Items.BUCKET) ? BREAK : PLACE;
            ProtectionDecisionEvent.Action action = item.is(Items.BUCKET)
                    ? ProtectionDecisionEvent.Action.BUCKET_FILL : ProtectionDecisionEvent.Action.BUCKET_EMPTY;
            if (!context.check(target, action, flags, "use that bucket")) {
                deny(event);
                return;
            }
        }

        // Vehicle placement has its own location and flag check, but a held
        // vehicle item must not bypass an interactive block such as a chest.
        if ((item.getItem() instanceof BoatItem || item.getItem() instanceof MinecartItem)
                && !isInteractive(level, pos, state)) return;
        // A plain block face is a placement target, not a use of that block.
        if (item.getItem() instanceof BlockItem && !isInteractive(level, pos, state)) return;
        if (context == null) context = decisions.context(player, level);
        StateFlag[] flags = interactionFlags(state);
        if (!context.check(pos, ProtectionDecisionEvent.Action.USE, flags, "use that")) {
            deny(event);
            return;
        }
        if (state.getBlock() instanceof BedBlock) {
            BlockPos other = pos.relative(state.getValue(BedBlock.PART) == BedPart.FOOT
                    ? state.getValue(BedBlock.FACING)
                    : state.getValue(BedBlock.FACING).getOpposite());
            if (!context.check(other, ProtectionDecisionEvent.Action.USE, BED, "sleep")) {
                deny(event);
            }
        }
    }

    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)
                || !decisions.active() || !isBucket(event.getItemStack())) return;
        // A fluid surface often yields RightClickItem rather than RightClickBlock.
        ServerLevel level = player.serverLevel();
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getViewVector(1.0f).scale(player.blockInteractionRange()));
        BlockHitResult hit = level.clip(new ClipContext(start, end, ClipContext.Block.OUTLINE,
                ClipContext.Fluid.ANY, player));
        if (hit.getType() != HitResult.Type.BLOCK) return;
        BlockPos pos = hit.getBlockPos();
        boolean fill = event.getItemStack().is(Items.BUCKET);
        BlockState state = level.getBlockState(pos);
        BlockPos target = fill || isFluidContainerTarget(state) || state.canBeReplaced()
                ? pos : pos.relative(hit.getDirection());
        if (!decisions.context(player, level).check(target,
                fill ? ProtectionDecisionEvent.Action.BUCKET_FILL : ProtectionDecisionEvent.Action.BUCKET_EMPTY,
                fill ? BREAK : PLACE, "use that bucket")) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (event.isCanceled() || event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START
                || !(event.getEntity() instanceof ServerPlayer player) || !decisions.active()) return;
        // The cancellable BreakEvent handles mining. This covers Block#attack,
        // which may mutate cake or unknown modded blocks before a break occurs.
        ServerLevel level = player.serverLevel();
        BlockState state = level.getBlockState(event.getPos());
        if (state.getBlock() instanceof net.minecraft.world.level.block.CakeBlock
                || state.getBlock() instanceof BigDripleafBlock
                || !state.getBlock().builtInRegistryHolder().key().location().getNamespace().equals("minecraft")) {
            if (!decisions.context(player, player.serverLevel()).check(event.getPos(),
                    ProtectionDecisionEvent.Action.USE,
                    state.getBlock() instanceof net.minecraft.world.level.block.CakeBlock ? BUILD_ONLY
                            : state.getBlock() instanceof BigDripleafBlock ? DRIPLEAF : INTERACT,
                    "use that")) {
                event.setCanceled(true);
                return;
            }
        }
        BlockPos adjacent = event.getPos().relative(event.getFace());
        if (level.getBlockState(adjacent).getBlock() instanceof BaseFireBlock
                && !decisions.context(player, level).check(adjacent, ProtectionDecisionEvent.Action.BREAK,
                BREAK, "put out that fire")) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onBoneMeal(BonemealEvent event) {
        if (event.isCanceled() || !(event.getPlayer() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level) || !decisions.active()) return;
        if (!decisions.context(player, level).check(event.getPos(), ProtectionDecisionEvent.Action.BONE_MEAL,
                PLACE, "grow that")) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onItemOnBlock(UseItemOnBlockEvent event) {
        if (event.isCanceled() || !(event.getPlayer() instanceof ServerPlayer player) || !decisions.active()) return;
        ItemStack item = event.getItemStack();
        if (item.getItem() instanceof BoatItem || item.getItem() instanceof MinecartItem) return;
        if (item.isEmpty() || item.getItem() instanceof BlockItem || isBucket(item) || isIgnition(item)
                || item.is(Items.BONE_MEAL)) return;
        boolean modded = !item.getItem().builtInRegistryHolder().key().location().getNamespace().equals("minecraft");
        if (event.getUsePhase() != (modded ? UseItemOnBlockEvent.UsePhase.ITEM_BEFORE_BLOCK
                : UseItemOnBlockEvent.UsePhase.ITEM_AFTER_BLOCK)) return;
        // Upstream conservatively treats unknown items applied to blocks as terrain modifications.
        if (!decisions.context(player, player.serverLevel()).check(event.getPos(),
                ProtectionDecisionEvent.Action.ITEM_ON_BLOCK, PLACE, "use that item")) {
            event.cancelWithResult(net.minecraft.world.ItemInteractionResult.FAIL);
        }
    }

    @SubscribeEvent
    public void onTrample(BlockEvent.FarmlandTrampleEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level) || !decisions.active()) return;
        if (WorldGuard.getInstance().getPlatform().getGlobalStateManager()
                .get(NeoForgeAdapter.adapt(level)).disablePlayerCropTrampling) {
            event.setCanceled(true);
            return;
        }
        ProtectionDecisionService.Context context = decisions.context(player, level);
        if (!context.check(event.getPos(), ProtectionDecisionEvent.Action.TRAMPLE, TRAMPLE_BREAK, "trample that")
                || !context.check(event.getPos(), ProtectionDecisionEvent.Action.TRAMPLE, TRAMPLE_PLACE, "trample that")) {
            event.setCanceled(true);
        }
    }

    private static void deny(PlayerInteractEvent.RightClickBlock event) {
        event.setCancellationResult(InteractionResult.FAIL);
        event.setCanceled(true);
    }

    private static boolean isInventory(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.is(Blocks.JUKEBOX) || state.is(Blocks.CHISELED_BOOKSHELF)
                || state.is(Blocks.CRAFTER)) return true;
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity instanceof Container) return true;
        // Bukkit treats these as USE/INTERACT rather than CHEST_ACCESS.
        if (state.is(Blocks.LECTERN) || state.is(Blocks.ENCHANTING_TABLE)
                || state.is(Blocks.LOOM) || state.is(Blocks.CARTOGRAPHY_TABLE)
                || state.is(Blocks.STONECUTTER) || state.is(Blocks.GRINDSTONE)
                || state.is(Blocks.CRAFTING_TABLE) || state.is(Blocks.ANVIL)
                || state.is(Blocks.CHIPPED_ANVIL) || state.is(Blocks.DAMAGED_ANVIL)) return false;
        if (entity instanceof MenuProvider) return true;
        return state.getMenuProvider(level, pos) != null
                && !state.getBlock().builtInRegistryHolder().key().location().getNamespace().equals("minecraft");
    }

    private static boolean isInteractive(ServerLevel level, BlockPos pos, BlockState state) {
        return state.getMenuProvider(level, pos) != null
                || !state.getBlock().builtInRegistryHolder().key().location().getNamespace().equals("minecraft")
                || state.is(BlockTags.BUTTONS) || state.is(BlockTags.DOORS)
                || state.is(BlockTags.TRAPDOORS) || state.is(BlockTags.FENCE_GATES)
                || state.is(BlockTags.PRESSURE_PLATES)
                || state.is(Blocks.TNT)
                || state.getBlock() instanceof BedBlock || state.getBlock() instanceof RespawnAnchorBlock;
    }

    private static StateFlag[] interactionFlags(BlockState state) {
        if (state.getBlock() instanceof BigDripleafBlock) return DRIPLEAF;
        if (state.getBlock() instanceof AnvilBlock) return ANVIL;
        if (state.getBlock() instanceof BedBlock) return BED;
        if (state.getBlock() instanceof RespawnAnchorBlock) return ANCHOR;
        if (state.is(Blocks.TNT)) return TNT;
        if (state.is(Blocks.REPEATER) || state.is(Blocks.COMPARATOR) || state.is(Blocks.CAKE)
                || state.is(Blocks.DRAGON_EGG) || state.is(BlockTags.FLOWER_POTS)
                || state.is(BlockTags.CANDLES) || state.is(BlockTags.CANDLE_CAKES)
                || state.is(BlockTags.ALL_SIGNS)) return BUILD_ONLY;
        if (state.is(BlockTags.BUTTONS) || state.is(BlockTags.DOORS)
                || state.is(BlockTags.TRAPDOORS) || state.is(BlockTags.FENCE_GATES)
                || state.is(BlockTags.PRESSURE_PLATES)
                || state.getBlock() instanceof net.minecraft.world.level.block.LeverBlock
                || state.getBlock() instanceof net.minecraft.world.level.block.BellBlock
                || state.getBlock() instanceof net.minecraft.world.level.block.LecternBlock
                || state.is(Blocks.ENCHANTING_TABLE) || state.is(Blocks.LOOM)
                || state.is(Blocks.CARTOGRAPHY_TABLE) || state.is(Blocks.STONECUTTER)
                || state.is(Blocks.GRINDSTONE) || state.is(Blocks.VAULT)) return USE;
        return INTERACT;
    }

    private static boolean isIgnition(ItemStack item) {
        return item.canPerformAction(ItemAbilities.FIRESTARTER_LIGHT);
    }

    private static boolean isFluidContainerTarget(BlockState state) {
        return state.hasProperty(BlockStateProperties.WATERLOGGED)
                && !state.getValue(BlockStateProperties.WATERLOGGED);
    }

    private static boolean isBucket(ItemStack item) {
        return item.is(Items.BUCKET) || item.getItem() instanceof BucketItem;
    }
}
