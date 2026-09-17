package com.axiom.client.fx;

/**
 * Bir yumruk aninda dogan ve EMITTER_DURATION_NANOS (2.5s) boyunca PERIYODIK
 * olarak yeni ruzgar tuyu ciftleri "pulse"layan yayici (client-only).
 *
 * NEDEN GEREKLI: onceki tasarimda tek bir SABIT origin (server tick
 * anindaki karakter pozisyonu) kullaniliyordu - ama boşa sallanan bir
 * yumrukta karakter o anda hala RETURNING/toparlanma pozuna geciyor
 * olabiliyor, bu yuzden efekt "karakterin arkasinda bir yerden" cikiyormus
 * gibi goruluyordu. Artik sabit koordinat yerine entityId tutuluyor ve her
 * pulse'ta WindTrailFxRenderer.processVacuumEmitters() karakterin O ANKI
 * guncel pozisyon+bakisindan origin'i YENIDEN hesapliyor - karakter
 * toparlanip yerlesince sonraki pulse'lar dogru noktadan cikiyor.
 */
final class VacuumEmitterFx {
    final int entityId;
    final float power;
    final long spawnNanos;
    long lastPulseNanos; // mutable - her pulse sonrasi guncellenir

    VacuumEmitterFx(int entityId, float power, long spawnNanos, long lastPulseNanos) {
        this.entityId = entityId;
        this.power = power;
        this.spawnNanos = spawnNanos;
        this.lastPulseNanos = lastPulseNanos;
    }
}
