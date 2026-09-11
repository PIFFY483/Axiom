package com.axiom.client.camera;

import com.axiom.Axiom;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * HitStopController / ScreenShakeController / FovController kendi baslarina
 * ilerlemez - biri onlarin tick()'ini her render frame'de cagirmali.
 * Bu sinif o gorevi ustleniyor.
 *
 * Sira onemli:
 *   1) HitStopController.tick(rawDeltaMs)  -> bu frame'in timeScale'ini uretir
 *   2) ScreenShakeController.tick(scaledDeltaSec) -> shake, hitstop'a gore
 *      YAVASLAR (yavas cekimde sarsinti da yavas ilerler - ScreenShakeController'daki
 *      "HitStop aktifken scaledDeltaSeconds gonderilir" notuna bkz.)
 *   3) FovController.tick(rawDeltaSec) -> FOV punch'lar hitstop'tan bagimsiz,
 *      normal hizda oynar (punch anindaki "hiz hissi" yavas cekimde de belirgin kalsin diye)
 *
 * NOT: Bu sinif sadece degerleri hesaplar (shakeAmount/offset/roll/fovPunch/
 * timeScale). Bu degerleri fiili render'a (GameRenderer FOV, kamera pozisyonu)
 * uygulamak icin ayrica bir mixin gerekiyor - o kisim henuz eklenmedi.
 */
@Mod.EventBusSubscriber(modid = Axiom.MOD_ID, value = Dist.CLIENT)
public class CameraFxTickHandler {

    private static long lastFrameNanos = 0L;

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            // Dunya yokken (ana menu vb.) sayaci resetle ki donuste dev bir
            // delta ile sistemlere "sicrama" yaptirmayalim.
            lastFrameNanos = 0L;
            return;
        }

        long now = System.nanoTime();
        if (lastFrameNanos == 0L) {
            lastFrameNanos = now;
            return;
        }

        float rawDeltaMs = (now - lastFrameNanos) / 1_000_000.0f;
        lastFrameNanos = now;

        // Sekme degistirme, breakpoint, lag spike gibi anormal buyuk delta'lari
        // sinirla (100ms) - yoksa hitstop/shake tek frame'de bitiverir.
        if (rawDeltaMs > 100.0f) rawDeltaMs = 100.0f;
        if (mc.isPaused()) return;

        float rawDeltaSec = rawDeltaMs / 1000.0f;

        float timeScale = HitStopController.tick(rawDeltaMs);
        ScreenShakeController.tick(rawDeltaSec * timeScale);
        FovController.tick(rawDeltaSec);
    }
}
