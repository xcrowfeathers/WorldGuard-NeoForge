package de.kuro.worldguard.protection;

import com.sk89q.worldguard.commands.CommandUtils;
import com.sk89q.worldguard.config.WorldConfiguration;
import de.kuro.worldguard.platform.NeoForgeWorldGuardPlatform;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

public final class BuildPermissionListener {
    private final NeoForgeWorldGuardPlatform platform;
    private final EntityInteractionPair entityInteractions = new EntityInteractionPair();

    public BuildPermissionListener(NeoForgeWorldGuardPlatform platform) {
        this.platform = platform;
    }

    public void forget(ServerPlayer player) { entityInteractions.forget(player); }
    public void clear() { entityInteractions.clear(); }

    private WorldConfiguration enabled(ServerLevel level) {
        if (platform.getRegionContainer() == null) return null;
        WorldConfiguration config = platform.getGlobalStateManager().get(platform.adaptWorld(level));
        return config.buildPermissions ? config : null;
    }

    private static String name(ResourceLocation id) {
        return id.getNamespace().equals("minecraft") ? id.getPath() : id.toString();
    }

    private boolean permits(ServerPlayer player, WorldConfiguration config,
                            String kind, String target, String action) {
        var actor = platform.adapt(player);
        if (actor.hasPermission("worldguard.build." + kind + "." + target + "." + action)
                || actor.hasPermission("worldguard.build." + kind + "." + action + "." + target)) return true;
        if (!config.buildPermissionDenyMessage.isEmpty()) {
            actor.printRaw(CommandUtils.replaceColorMacros(config.buildPermissionDenyMessage));
        }
        return false;
    }

    private boolean block(ServerPlayer player, WorldConfiguration config, BlockState state, String action) {
        return permits(player, config, "block", name(BuiltInRegistries.BLOCK.getKey(state.getBlock())), action);
    }

    @SubscribeEvent
    public void onBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled() || !(event.getPlayer() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level)) return;
        WorldConfiguration config = enabled(level);
        if (config != null && !block(player, config, event.getState(), "remove")) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level)) return;
        WorldConfiguration config = enabled(level);
        if (config == null) return;
        if (event instanceof BlockEvent.EntityMultiPlaceEvent many) {
            for (BlockSnapshot snapshot : many.getReplacedBlockSnapshots()) {
                if (!block(player, config, snapshot.getCurrentState(), "place")) {
                    event.setCanceled(true);
                    return;
                }
            }
        } else if (!block(player, config, event.getBlockSnapshot().getCurrentState(), "place")) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)) return;
        WorldConfiguration config = enabled(player.serverLevel());
        if (config != null && !block(player, config, player.serverLevel().getBlockState(event.getPos()), "interact")) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onUseItem(PlayerInteractEvent.RightClickItem event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)) return;
        WorldConfiguration config = enabled(player.serverLevel());
        ItemStack stack = event.getItemStack();
        if (config == null || stack.isEmpty() || stack.getItem() instanceof BlockItem) return;
        if (!permits(player, config, "item", name(BuiltInRegistries.ITEM.getKey(stack.getItem())), "use")) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onUseEntity(PlayerInteractEvent.EntityInteractSpecific event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)) return;
        WorldConfiguration config = enabled(player.serverLevel());
        if (config != null && !permits(player, config, "entity",
                name(BuiltInRegistries.ENTITY_TYPE.getKey(event.getTarget().getType())), "interact")) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
        } else {
            entityInteractions.record(player, event.getTarget(), event.getHand());
        }
    }

    @SubscribeEvent
    public void onUseEntityGeneral(PlayerInteractEvent.EntityInteract event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)) return;
        if (entityInteractions.alreadyChecked(player, event.getTarget(), event.getHand())) return;
        WorldConfiguration config = enabled(player.serverLevel());
        if (config != null && !permits(player, config, "entity",
                name(BuiltInRegistries.ENTITY_TYPE.getKey(event.getTarget().getType())), "interact")) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onDamageEntity(AttackEntityEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)) return;
        WorldConfiguration config = enabled(player.serverLevel());
        if (config != null && !permits(player, config, "entity",
                name(BuiltInRegistries.ENTITY_TYPE.getKey(event.getTarget().getType())), "damage")) {
            event.setCanceled(true);
        }
    }
}
