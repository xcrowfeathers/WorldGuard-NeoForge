package de.kuro.worldguard.protection;

import de.kuro.worldguard.platform.NeoForgeWorldGuardPlatform;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.alchemy.PotionContents;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.Set;

/** Upstream blocked-potion item rule at the cancellable player use event. */
public final class BlockedPotionListener {
    private final NeoForgeWorldGuardPlatform platform;

    public BlockedPotionListener(NeoForgeWorldGuardPlatform platform) {
        this.platform = platform;
    }

    @SubscribeEvent
    public void onUse(PlayerInteractEvent.RightClickItem event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)
                || platform.getRegionContainer() == null) return;
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof PotionItem)) return;
        Set<String> blocked = platform.blockedPotionEffects(player.serverLevel());
        if (blocked.isEmpty()) return;
        PotionContents contents = stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        String matched = null;
        for (var effect : contents.getAllEffects()) {
            String id = BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value()).toString();
            if (blocked.contains(id)) {
                matched = id;
                break;
            }
        }
        if (matched == null) return;
        boolean thrown = stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION);
        boolean bypass = platform.adapt(player).hasPermission("worldguard.override.potions");
        if (bypass && !(thrown && platform.blockPotionsAlways(player.serverLevel()))) return;
        player.sendSystemMessage(Component.literal("Sorry, potions with " + matched
                + " are presently disabled."));
        event.setCancellationResult(InteractionResult.FAIL);
        event.setCanceled(true);
    }
}
