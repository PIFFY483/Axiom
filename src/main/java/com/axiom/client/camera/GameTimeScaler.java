package com.axiom.client.camera;

/**
 * Minecraft'in render dongusundeki Timer.advanceTime(long) cagrisina,
 * gercek sistem saati yerine HitStopController'in o anki timeScale'ine
 * gore YAVASLATILMIS bir "sanal" zaman besler.
 *
 * Neden gerekli: HitStopController tek basina sadece bir timeScale (0..1)
 * SAYISI uretiyordu, hicbir yere uygulanmiyordu - o yuzden hitstop
 * "hesaplaniyor ama gorunmuyordu". Bu sinif o sayiyi motorun asil zaman
 * kaynagina baglayarak GERCEK bir yavaslama (partial tick / world
 * interpolasyonu / mob animasyonlari) uretiyor.
 *
 * Nasil calisir: her render frame'de gercek zamanda ne kadar ms gectigini
 * (realDelta) hesaplar, bunu timeScale ile carpip kendi biriktirdigi
 * "sanal ms" sayacina ekler ve Timer'a GERCEK ms yerine bu sanal ms'yi
 * verir. HitStopController'in kendi ilerleme sayaci (elapsed) ayri ve
 * HER ZAMAN gercek zamanda ilerliyor (MinecraftTimerMixin -> GameTimeScaler
 * -> HitStopController.peek() sirasi, bkz. o siniflar) - yani "500ms'lik
 * hitstop" gercekten 500 gercek ms surer, sadece o sure icinde OYUN
 * DUNYASI yavas akar.
 *
 * MinecraftTimerMixin bunu cagirir; require = 0 ile kaydedildigi icin
 * (bkz. axiom.mixins.json) mixin hedefi tutmazsa oyun crash olmaz, sadece
 * bu ozellik sessizce devre disi kalir.
 */
public final class GameTimeScaler {

    private static long lastRealMs = -1L;
    private static long virtualMs = -1L;

    private GameTimeScaler() {}

    public static long scale(long realMs) {
        if (lastRealMs < 0L) {
            lastRealMs = realMs;
            virtualMs = realMs;
            return virtualMs;
        }

        long realDelta = realMs - lastRealMs;
        lastRealMs = realMs;
        if (realDelta < 0L) realDelta = 0L;      // saat geri sardiysa (sistem uykudan uyanma vb.)
        if (realDelta > 250L) realDelta = 250L;   // lag spike'ta devasa sicramayi engelle

        float scale = HitStopController.peek(); // 1.0 = normal, ~0.02 = neredeyse donmus
        virtualMs += Math.round(realDelta * scale);
        return virtualMs;
    }
}
