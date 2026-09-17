package com.axiom.client.fx;

/**
 * Tek bir dash "ucus ruzgari" efektinin calisma-zamani verisi (client-only).
 *
 * Karakter dash boyunca hareket ettigi icin sabit bir spawn-noktasi tutmuyor -
 * entityId uzerinden WindTrailFxRenderer her frame'de guncel pozisyonu okuyup
 * cizgileri ona gore yeniden hesapliyor (bkz. WindTrailFxRenderer.renderFlightTrails).
 */
final class FlightWindFx {
    final int entityId;
    final float dirX, dirZ;   // dash'in yatay yonu (birim vektor, spawn aninda sabitlenir)
    final float power;        // 0-1, sarj orani
    final long spawnNanos;
    final long durationNanos;

    // Her cizgi govde etrafinda kendi sabit acisinda/yaricapinda doner ve
    // kendi fazindan baslayarak dogup-kaybolur - boylece hepsi ayni anda
    // "pat" diye belirip kaybolmaz, surekli akan bir ruzgar hissi olusur.
    final float[] angle;
    final float[] radius;
    final float[] phase;

    FlightWindFx(int entityId, float dirX, float dirZ, float power, long spawnNanos, long durationNanos,
                 float[] angle, float[] radius, float[] phase) {
        this.entityId = entityId;
        this.dirX = dirX;
        this.dirZ = dirZ;
        this.power = power;
        this.spawnNanos = spawnNanos;
        this.durationNanos = durationNanos;
        this.angle = angle;
        this.radius = radius;
        this.phase = phase;
    }
}
