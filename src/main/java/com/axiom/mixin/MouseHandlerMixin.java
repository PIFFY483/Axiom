package com.axiom.mixin;

import com.axiom.client.SkillLookLockClientState;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skill BAKIS YONU kilidi aktifken (bkz. SkillLookLockClientState - bu SADECE
 * dash+impact penceresinde aktif, sarj asamasinda DEGIL) fare hareketinin
 * oyuncuyu DONDURMESINI kaynaginda engeller.
 *
 * ClientTickHandler'daki "her tick sonunda yRot/xRot'u kilitli aciya geri
 * dondurme" yaklasimi TEK BASINA yeterli degildi: turnPlayer() her RENDER
 * FRAME'inde (tick'ten daha sik) cagrilir, bu yuzden iki tick arasinda kisa
 * bir "kacip geri sicrama" hissi olusabiliyordu. Bu mixin, kilit aktifken
 * turnPlayer()'in govdesini TAMAMEN atlayarak fare girdisinin rotasyona hic
 * ETKI ETMEMESINI saglar - kilit aciktan itibaren bakis %100 sabit kalir.
 * ClientTickHandler'daki tick-sonu duzeltme, sigorta olarak duruyor.
 *
 * require = 0: obfuscation'a duyarli bir hedef - bir surum guncellemesinde
 * tutmazsa oyun acilmasin diye degil, bu ozellik sessizce devre disi kalsin
 * diye kapatildi (bkz. MinecraftTimerMixin'deki ayni gerekce).
 */
@Mixin(MouseHandler.class)
public class MouseHandlerMixin {

    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true, require = 0)
    private void axiom$blockLookWhileSkillLocked(CallbackInfo ci) {
        if (SkillLookLockClientState.isLocked()) {
            ci.cancel();
        }
    }
}
