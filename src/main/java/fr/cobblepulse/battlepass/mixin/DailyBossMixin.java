package fr.cobblepulse.battlepass.mixin;

import fr.cobblepulse.battlepass.integration.BattletowerHooks;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Quetes "Boss du Jour" de la Tour de Combat (ignore si CobblePulse Battletower est absent). */
@Pseudo
@Mixin(targets = "fr.cobblepulse.battletower.tower.DailyBoss", remap = false)
public abstract class DailyBossMixin {
    @Inject(method = "onResult", at = @At("HEAD"), remap = false, require = 0)
    private static void cpbp$result(@Coerce Object outcome, @Coerce Object run, ServerPlayer player, CallbackInfo ci) {
        BattletowerHooks.bossResult(outcome, run);
    }
}
