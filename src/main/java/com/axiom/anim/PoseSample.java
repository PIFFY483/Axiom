package com.axiom.anim;

import org.joml.Quaternionf;

public record PoseSample(float rotX, float rotY, float rotZ,
                         float posX, float posY, float posZ,
                         Quaternionf rot) {
    /** Eski imza - quaternion yoksa Euler fallback kullanilir. */
    public PoseSample(float rotX, float rotY, float rotZ, float posX, float posY, float posZ) {
        this(rotX, rotY, rotZ, posX, posY, posZ, null);
    }
    public static final PoseSample IDENTITY = new PoseSample(0, 0, 0, 0, 0, 0, null);
}
