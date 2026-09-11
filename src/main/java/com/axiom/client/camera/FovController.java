package com.axiom.client.camera;

import net.minecraft.util.Mth;
import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;

/**
 * FovController
 *
 * Dinamik FOV yöneticisi.
 * - Modifier tabanlı: Her sistem (dash, sprint, charge, hitstop) kendi modifier'ını ekler.
 * - Zoom in/out: Pozitif değer FOV'u genişletir (hız hissi), negatif değer daraltır (zoom/odaklanma).
 * - Smooth entry/exit: Ani sıçramalar olmaz, exponential smoothing ile yumuşak geçer.
 * - Otomatik temizlik: Süresi dolan modifier'lar otomatik silinir.
 * - FPS bağımsız.
 *
 * Kullanım:
 *   FovController.addModifier("dash", 15.0f, 0.5f);    // 0.5 saniye boyunca FOV +15
 *   FovController.addModifier("sniper_zoom", -20.0f, 0); // Kalıcı zoom (0 = sonsuz)
 *   FovController.addPunch(-8.0f, 0.3f);               // Vuruş anında kısa FOV punch
 */
public class FovController {

    // ── Modifier Depolama ──
    private static final Map<String, FovModifier> activeModifiers = new HashMap<>();

    // ── Smoothing Durumu ──
    private static float currentFovModifier = 0.0f;
    private static float targetFovModifier = 0.0f;

    // ── Smoothing Hızları ──
    private static final float ATTACK_SPEED = 16.0f;  // Hedefe gitme hızı (zoom in/out)
    private static final float RELEASE_SPEED = 8.0f;  // Normale dönme hızı

    // ── Güvenlik Sınırları (Maksimum Zoom In/Out) ──
    // 100 hasar vuran silah FOV'u -90 yapıp ekranı bozamaz.
    private static final float MAX_FOV_INCREASE = 40.0f;   // Max genişleme
    private static final float MAX_FOV_DECREASE = -35.0f;  // Max daralma (zoom)

    // ══════════════════════════════════════════════════════
    //  İÇ SINIF: MODIFIER
    // ══════════════════════════════════════════════════════
    private static class FovModifier {
        final float amount;
        final float duration;
        float remainingTime;

        FovModifier(float amount, float duration) {
            // Güç clamp'lenir (min/max sınırları)
            this.amount = Mth.clamp(amount, MAX_FOV_DECREASE, MAX_FOV_INCREASE);
            this.duration = duration;
            this.remainingTime = duration;
        }

        boolean tickAndCheckExpiry(float deltaTime) {
            if (duration <= 0.0f) return false; // 0 veya negatif = kalıcı (manuel silinmeli)
            remainingTime -= deltaTime;
            return remainingTime <= 0.0f;
        }
    }

    // ══════════════════════════════════════════════════════
    //  GİRİŞ NOKTALARI (API)
    // ══════════════════════════════════════════════════════

    /**
     * FOV modifier ekle.
     *
     * @param id       Benzersiz kimlik (örn: "dash", "charge_shot", "hitstop_test")
     * @param amount   FOV değişimi. Pozitif = genişlet, Negatif = daralt (zoom).
     * @param duration Saniye cinsinden süre. <= 0 verirsen kalıcı olur.
     */
    public static void addModifier(String id, float amount, float duration) {
        activeModifiers.put(id, new FovModifier(amount, duration));
    }

    /**
     * Belirli bir modifier'ı manuel sil (örn: oyuncu charge'ı bıraktığında).
     */
    public static void removeModifier(String id) {
        activeModifiers.remove(id);
    }

    /**
     * Kısa süreli, otomatik çöken FOV punch (vuruş, patlama anları için).
     * ID otomatik üretilir.
     */
    public static void addPunch(float amount, float duration) {
        String id = "punch_" + System.nanoTime();
        addModifier(id, amount, duration);
    }

    /**
     * Tüm modifier'ları sıfırla (Ölüm, arena reset, boyut değişimi).
     */
    public static void clearAll() {
        activeModifiers.clear();
        currentFovModifier = 0.0f;
        targetFovModifier = 0.0f;
    }

    // ══════════════════════════════════════════════════════
    //  GÜNCELLEME (TICK)
    // ══════════════════════════════════════════════════════

    /**
     * Her frame/tick'te çağrılır.
     * @param deltaTime Saniye cinsinden geçen süre.
     */
    public static void tick(float deltaTime) {
        if (deltaTime <= 0.0f) return;

        // 1. Süresi dolan modifier'ları temizle ve yeni hedefi hesapla
        float newTarget = 0.0f;
        Iterator<Map.Entry<String, FovModifier>> it = activeModifiers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, FovModifier> entry = it.next();
            if (entry.getValue().tickAndCheckExpiry(deltaTime)) {
                it.remove(); // Süresi doldu, sil
            } else {
                newTarget += entry.getValue().amount;
            }
        }

        // Toplam hedefi sınırla
        targetFovModifier = Mth.clamp(newTarget, MAX_FOV_DECREASE, MAX_FOV_INCREASE);

        // 2. Mevcut değeri hedefe doğru yumuşat (Exponential Smoothing)
        // Hedefe gidiyorsak ATTACK, normale dönüyorsak RELEASE hızını kullan
        float speed = (Math.abs(targetFovModifier) > Math.abs(currentFovModifier) + 0.01f)
                ? ATTACK_SPEED
                : RELEASE_SPEED;

        float alpha = 1.0f - (float) Math.exp(-speed * deltaTime);
        currentFovModifier += (targetFovModifier - currentFovModifier) * alpha;

        // Çok küçük değerlerde sıfıra snap (kayan nokta hatalarını önle)
        if (Math.abs(currentFovModifier) < 0.01f && Math.abs(targetFovModifier) < 0.01f) {
            currentFovModifier = 0.0f;
        }
    }

    // ══════════════════════════════════════════════════════
    //  ÇIKIŞ (MIXIN OKUR)
    // ══════════════════════════════════════════════════════

    /**
     * GameRendererMixin tarafından çağrılır.
     * Vanilla FOV'un üzerine bizim hesapladığımız modifier'ı ekler.
     */
    public static double calculateFov(double baseFov, float partialTicks) {
        return baseFov + currentFovModifier;
    }

    // Debug için
    public static float getCurrentModifier() {
        return currentFovModifier;
    }
}