package fr.cobblepulse.battlepass.mixin;

import fr.cobblepulse.battlepass.quest.QuestHooks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Statistiques vanilla (fabrication, distance, elevage, statistique libre) pour les quetes. */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
    @Inject(method = "awardStat(Lnet/minecraft/stats/Stat;I)V", at = @At("HEAD"))
    private void cpbp$awardStat(Stat<?> stat, int amount, CallbackInfo ci) {
        QuestHooks.onStat((ServerPlayer) (Object) this, stat, amount);
    }
}
