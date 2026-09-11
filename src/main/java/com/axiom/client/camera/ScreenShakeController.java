package com.axiom.client.camera;

import net.minecraft.util.Mth;

/**
 * ScreenShakeController
 *
 * Trauma tabanlı ekran sarsıntısı.
 * - Power/scale bazlı: az hasar → az sallantı, çok hasar → çok sallantı
 * - Smooth entry: sarsıntı aniden başlamaz, yumuşak girer
 * - Smooth exit: sarsıntı aniden durmaz, yumuşak çıkar
 * - Max 3 saniye: saniyeler toplandığında 3 saniyeyi geçemez
 * - FPS bağımsız
 * - Client-only (server gameplay etkilenmez)
 *
 * Kullanım:
 *   ScreenShakeController.addShake(0.5f, 0.3f);       // Güç + süre
 *   ScreenShakeController.addShakeFromDamage(15.0f);   // Hasardan otomatik
 *   ScreenShakeController.addTrauma(0.6f);             // Direkt trauma (boss slam)
 */
public class ScreenShakeController {

    // ── Çekirdek Durum ──
    private static float targetTrauma = 0.0f;   // Hedeflenen trauma (0..1)
    private static float smoothTrauma = 0.0f;   // Yumuşatılmış trauma (render için)
    private static float remainingTime = 0.0f;  // Kalan sallantı süresi (saniye)
    private static float noiseTime = 0.0f;      // Noise zamanı

    // ── Sınırlar ──
    private static final float MAX_TRAUMA = 1.0f;
    private static final float MAX_DURATION = 3.0f;       // Max 3 saniye toplam
    private static final float MIN_POWER = 0.05f;
    private static final float MAX_POWER = 1.0f;
    private static final float MIN_DURATION = 0.05f;

    // ── Smooth Parametreleri ──
    // Attack: sarsıntının ne kadar hızlı "girdiği" (yüksek = hızlı giriş)
    private static final float ATTACK_SPEED = 14.0f;
    // Release: süre bittikten sonra ne kadar hızlı "çıktığı"
    private static final float RELEASE_SPEED = 4.0f;

    // ── Çıktı Değerleri ──
    private static float shakeAmount = 0.0f;
    private static float offsetX = 0.0f;
    private static float offsetY = 0.0f;
    private static float rollOffset = 0.0f;
    private static float fovPunch = 0.0f;

    // ── Maksimum Offset Değerleri ──
    private static final float MAX_OFFSET = 0.35f;     // blok cinsinden
    private static final float MAX_ROLL = 2.5f;        // derece
    private static final float MAX_FOV_PUNCH = 4.0f;   // derece

    // ══════════════════════════════════════════════════════
    //  GİRİŞ NOKTALARI
    // ══════════════════════════════════════════════════════

    /**
     * Sarsıntı ekle. Güç ve süre clamp'lenir.
     *
     * @param power    Sarsıntı gücü (0.05 - 1.0 arası clamp'lenir)
     * @param duration Sarsıntı süresi saniye (max toplam 3s)
     */
    public static void addShake(float power, float duration) {
        power = Mth.clamp(power, MIN_POWER, MAX_POWER);
        duration = Mth.clamp(duration, MIN_DURATION, MAX_DURATION);

        // Trauma stack'le (max 1.0)
        targetTrauma = Math.min(targetTrauma + power, MAX_TRAUMA);

        // Süre stack'le (max 3 saniye)
        remainingTime = Math.min(remainingTime + duration, MAX_DURATION);
    }

    /**
     * Hasardan otomatik ölçekle.
     * 20 hasar = tam güç. Üstü clamp'lenir.
     *
     * @param damage Hasar değeri
     */
    public static void addShakeFromDamage(float damage) {
        float power = damage / 20.0f;
        float duration = 0.12f + power * 0.35f;  // 0.12s → 0.47s
        addShake(power, duration);
    }

    /**
     * Direkt trauma ekle (boss slam, explosion gibi büyük olaylar).
     * Kısa süre otomatik eklenir.
     *
     * @param amount Trauma miktarı (0..1)
     */
    public static void addTrauma(float amount) {
        targetTrauma = Math.min(targetTrauma + Mth.clamp(amount, 0.0f, 1.0f), MAX_TRAUMA);
        remainingTime = Math.min(remainingTime + 0.35f, MAX_DURATION);
    }

    /**
     * Hazır preset'ler — plan'daki olaylara karşılık gelir.
     */
    public static void minigunFire()    { addShake(0.02f, 0.05f); }
    public static void lightImpact()    { addShake(0.10f, 0.12f); }
    public static void heavyImpact()    { addShake(0.35f, 0.25f); }
    public static void explosion()      { addShake(0.55f, 0.40f); }
    public static void bossSlam()       { addShake(0.60f, 0.45f); }
    public static void playerHurt()     { addShake(0.20f, 0.18f); }

    // ══════════════════════════════════════════════════════
    //  GÜNCELLEME
    // ══════════════════════════════════════════════════════

    /**
     * Her render frame'de çağır. FPS bağımsız.
     *
     * @param deltaTime Saniye cinsinden geçen süre
     *                  (HitStop aktifken scaledDeltaSeconds gönderilir)
     */
    public static void tick(float deltaTime) {
        if (deltaTime <= 0.0f) return;

        noiseTime += deltaTime;

        if (remainingTime > 0.0f) {
            // ── AKTİF FAZ: hedefe doğru yumuşak giriş ──
            remainingTime -= deltaTime;
            if (remainingTime <= 0.0f) {
                remainingTime = 0.0f;
            }
            // Exponential smoothing ile targetTrauma'ya yaklaş
            float alpha = 1.0f - (float) Math.exp(-ATTACK_SPEED * deltaTime);
            smoothTrauma += (targetTrauma - smoothTrauma) * alpha;
        } else {
            // ── SÖNÜM FAZI: yumuşak çıkış ──
            targetTrauma = Math.max(0.0f, targetTrauma - RELEASE_SPEED * deltaTime);
            float alpha = 1.0f - (float) Math.exp(-RELEASE_SPEED * 2.0f * deltaTime);
            smoothTrauma += (targetTrauma - smoothTrauma) * alpha;
        }

        // Çok küçük değerlerde sıfıra snap (sonsuz küçülme döngüsünü önle)
        if (smoothTrauma < 0.002f && targetTrauma < 0.002f) {
            reset();
            return;
        }

        // ── SHAKE MİKTARI: trauma² formülü (plan'dan) ──
        shakeAmount = smoothTrauma * smoothTrauma;

        // ── NOISE TABANLI OFFSET ÜRET ──
        offsetX    = noise(noiseTime, 0.0f)   * MAX_OFFSET    * shakeAmount;
        offsetY    = noise(noiseTime, 137.0f)  * MAX_OFFSET    * shakeAmount;
        rollOffset = noise(noiseTime, 271.0f)  * MAX_ROLL      * shakeAmount;
        fovPunch   = noise(noiseTime, 419.0f)  * MAX_FOV_PUNCH * shakeAmount;
    }

    // ══════════════════════════════════════════════════════
    //  ÇIKIŞ NOKTALARI (CameraRig / VirtualCameraHandler okur)
    // ══════════════════════════════════════════════════════

    public static float getShakeAmount() { return shakeAmount; }
    public static float getOffsetX()     { return offsetX; }
    public static float getOffsetY()     { return offsetY; }
    public static float getRollOffset()  { return rollOffset; }
    public static float getFovPunch()    { return fovPunch; }
    public static boolean isActive()     { return smoothTrauma > 0.002f; }

    /** Tüm durumu sıfırla (arena reset, death, vb.) */
    public static void reset() {
        targetTrauma = 0.0f;
        smoothTrauma = 0.0f;
        remainingTime = 0.0f;
        noiseTime = 0.0f;
        shakeAmount = 0.0f;
        offsetX = 0.0f;
        offsetY = 0.0f;
        rollOffset = 0.0f;
        fovPunch = 0.0f;
    }

    // ══════════════════════════════════════════════════════
    //  NOISE (basit sin kombinasyonu, kütüphane gerektirmez)
    // ══════════════════════════════════════════════════════

    /**
     * Smooth pseudo-random noise.
     * Farklı frekans ve seed'lerle organik titreşim üretir.
     * Çıktı: -1..1 arası
     */
    private static float noise(float t, float seed) {
        float s = t + seed;
        return (float) (
                Math.sin(s * 23.7f)       * 0.45f +
                        Math.sin(s * 17.3f + 1.7f) * 0.30f +
                        Math.sin(s * 43.1f + 3.1f) * 0.15f +
                        Math.sin(s * 7.9f  + 5.3f) * 0.10f
        );
    }
}