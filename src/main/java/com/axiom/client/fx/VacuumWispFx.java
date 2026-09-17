package com.axiom.client.fx;

import net.minecraft.world.phys.Vec3;

/**
 * Yumruk aninda (isabet etsin, iskalasin ya da hic hedef olmasin -
 * farketmez) vurus noktasindan yukari dogru yukselen, hafifce sag-sola
 * salinan tek bir ruzgar tuyunun (wisp) verisi (client-only, immutable).
 *
 * ONCEKI TASARIM (spiral huni) BEGENILMEDI - kullanici geri bildirimi
 * uzerine tam sarmal donusun yerini, WindTrailFxRenderer'daki ucus
 * cizgileriyle AYNI gorsel dile (ince, kameraya-donuk, yari-saydam serit)
 * sahip, sadece hafifce salinarak yukselen bir tuy aldi.
 */
final class VacuumWispFx {
    final Vec3 origin;
    final float baseAngleDeg; // govde etrafindaki sabit baslangic acisi
    final float baseRadius;   // origin'den yatay uzaklik
    final float swayDir;      // +1 / -1 - hafif sallanma yonu (iki tuy ters yone salinsin diye)
    final long spawnNanos;
    final long durationNanos;

    VacuumWispFx(Vec3 origin, float baseAngleDeg, float baseRadius, float swayDir,
                 long spawnNanos, long durationNanos) {
        this.origin = origin;
        this.baseAngleDeg = baseAngleDeg;
        this.baseRadius = baseRadius;
        this.swayDir = swayDir;
        this.spawnNanos = spawnNanos;
        this.durationNanos = durationNanos;
    }
}
