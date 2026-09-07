package com.axiom.anim;

import java.util.Map;
import java.util.TreeMap;

/**
 * Tek bir kanal (rotation / position / scale) icin zaman -> deger keyframe listesi.
 * Blockbench formatinda zamanlar saniye cinsindendir.
 */
public class BBTrack {
    private final TreeMap<Float, BBVector> keyframes = new TreeMap<>();

    public void put(float time, BBVector value) {
        keyframes.put(time, value);
    }

    public boolean isEmpty() {
        return keyframes.isEmpty();
    }

    /**
     * Verilen zamandaki degeri, cevresindeki iki keyframe arasinda lineer
     * interpolasyonla hesaplar. Zaman araligin disindaysa en yakin uctaki
     * degeri dondurur (clamp).
     */
    public BBVector sample(float time) {
        if (keyframes.isEmpty()) return BBVector.zero();

        Map.Entry<Float, BBVector> floor = keyframes.floorEntry(time);
        Map.Entry<Float, BBVector> ceil = keyframes.ceilingEntry(time);

        if (floor == null) return ceil.getValue();
        if (ceil == null) return floor.getValue();
        if (floor.getKey().equals(ceil.getKey())) return floor.getValue();

        float span = ceil.getKey() - floor.getKey();
        float t = span <= 0f ? 0f : (time - floor.getKey()) / span;
        return BBVector.lerp(floor.getValue(), ceil.getValue(), t);
    }
}
