package com.axiom.mixin;

import com.axiom.client.camera.GameTimeScaler;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Minecraft.runTick() icinde her frame'de this.timer.advanceTime(Util.getMillis())
 * cagrilir - dunyanin kac tick islenecegini VE render partialTick'i bu
 * cagridan gelen zamana gore hesaplanir. Buraya gercek sistem saati yerine
 * GameTimeScaler.scale(...) ile YAVASLATILMIS bir "sanal" zaman vererek
 * HitStopController'in timeScale'ini oyunun GERCEK render/tick akisina
 * baglar (bkz. GameTimeScaler javadoc).
 *
 * require = 0: bu enjeksiyon Mojang mapping'lerine cok siki bagli
 * (obfuscation'a duyarli) bir cagriyi hedefliyor. Bir surum guncellemesinde
 * hedef tutmazsa oyunun TAMAMEN acilmamasi yerine, bu ozellik sessizce
 * devre disi kalsin diye required kontrolu kapatildi.
 */
@Mixin(Minecraft.class)
public class MinecraftTimerMixin {

    @ModifyArg(
            method = "runTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/Timer;advanceTime(J)I"
            ),
            require = 0
    )
    private long axiom$scaleTimerAdvance(long currentTimeMillis) {
        return GameTimeScaler.scale(currentTimeMillis);
    }
}
