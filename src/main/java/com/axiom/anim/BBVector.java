package com.axiom.anim;

public class BBVector {
    public final float x, y, z;

    public BBVector(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static BBVector lerp(BBVector a, BBVector b, float t) {
        return new BBVector(
                a.x + (b.x - a.x) * t,
                a.y + (b.y - a.y) * t,
                a.z + (b.z - a.z) * t
        );
    }

    public static BBVector zero() {
        return new BBVector(0, 0, 0);
    }
}
