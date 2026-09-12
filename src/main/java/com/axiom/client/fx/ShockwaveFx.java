package com.axiom.client.fx;

import net.minecraft.world.phys.Vec3;

/** Tek bir sok dalgasi halkasinin calisma-zamani verisi (client-only, immutable). */
final class ShockwaveFx {
    final Vec3 center;
    final Vec3 right; // halka duzleminin "sag" ekseni
    final Vec3 up;    // halka duzleminin "yukari" ekseni (dunya yukarisi VEYA vurus yonune dik yerel yukari)
    final float maxRadius;
    final long spawnNanos;    // bu ANDAN ITIBAREN gorunur olur - kademeli halkalar icin gelecekte olabilir
    final long durationNanos;

    // YENI: her kayit kendi gorsel stilini tasir - boylece dash'in arkasindan
    // cikan mavi enerji halkasi ile yumruk vurus anindaki sok dalgasi (bkz.
    // ShockwaveFxRenderer.spawnPunchBurst) AYNI render dongusunu (ACTIVE listesi,
    // onRenderLevelStage, drawRing) paylasip yine de farkli gorunebiliyor.
    final float startRadius;
    final float ringThickness;
    final float colorR, colorG, colorB;

    ShockwaveFx(Vec3 center, Vec3 right, Vec3 up, float maxRadius, long spawnNanos, long durationNanos,
                float startRadius, float ringThickness, float colorR, float colorG, float colorB) {
        this.center = center;
        this.right = right;
        this.up = up;
        this.maxRadius = maxRadius;
        this.spawnNanos = spawnNanos;
        this.durationNanos = durationNanos;
        this.startRadius = startRadius;
        this.ringThickness = ringThickness;
        this.colorR = colorR;
        this.colorG = colorG;
        this.colorB = colorB;
    }
}
