package de.kuro.worldguard.mixin;

import de.kuro.worldguard.protection.EnvironmentalHooks;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

@Mixin(ThrownPotion.class)
public abstract class ThrownPotionSplashMixin {
    @Redirect(method = "applySplash", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"))
    private List<LivingEntity> worldguard$splash(Level level, Class<LivingEntity> type, AABB box) {
        List<LivingEntity> affected = level.getEntitiesOfClass(type, box);
        if (level.isClientSide || affected.isEmpty()) return affected;
        ThrownPotion potion = (ThrownPotion) (Object) this;
        affected.removeIf(target -> !EnvironmentalHooks.potionSplash(potion, target));
        return affected;
    }
}
