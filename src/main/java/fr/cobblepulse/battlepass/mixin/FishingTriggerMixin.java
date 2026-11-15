package fr.cobblepulse.battlepass.mixin;

import fr.cobblepulse.battlepass.quest.QuestHooks;
import net.minecraft.advancements.critereon.FishingRodHookedTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;

/** Peche a la canne vanilla (objets pris) pour les quetes de peche. */
@Mixin(FishingRodHookedTrigger.class)
public abstract class FishingTriggerMixin {
    @Inject(method = "trigger", at = @At("HEAD"))
    private void cpbp$fished(ServerPlayer player, ItemStack rod, FishingHook hook, Collection<ItemStack> items, CallbackInfo ci) {
        QuestHooks.onFished(player, items);
    }
}
