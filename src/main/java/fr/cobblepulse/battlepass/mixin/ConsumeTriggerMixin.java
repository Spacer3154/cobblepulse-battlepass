package fr.cobblepulse.battlepass.mixin;

import fr.cobblepulse.battlepass.quest.QuestHooks;
import net.minecraft.advancements.critereon.ConsumeItemTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Aliments consommes pour les quetes "manger". */
@Mixin(ConsumeItemTrigger.class)
public abstract class ConsumeTriggerMixin {
    @Inject(method = "trigger", at = @At("HEAD"))
    private void cpbp$consumed(ServerPlayer player, ItemStack item, CallbackInfo ci) {
        QuestHooks.onConsumed(player, item);
    }
}
