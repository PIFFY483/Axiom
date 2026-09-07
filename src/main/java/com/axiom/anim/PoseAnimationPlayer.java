package com.axiom.anim;

import net.minecraft.client.model.geom.ModelPart;
import org.joml.Quaternionf;
import java.util.HashMap;
import java.util.Map;

public class PoseAnimationPlayer {
    public enum Phase { IDLE, CHARGING, RELEASING, RETURNING }

    private static final float DEFAULT_RETURN_DURATION = 0.25f;

    private final PoseAnimation animation;
    private final float chargeCap;
    private final float returnDuration;
    private Phase phase = Phase.IDLE;
    private long phaseStartNanos = 0L;
    private float phaseStartElapsed = 0f;
    private boolean restorePending = false;

    private static final class Pivot {
        float x, y, z;
        float rx, ry, rz;
        boolean captured = false;
    }

    private static final class ExitPose {
        Quaternionf rot;              // render icin: SLERP edilen world quaternion
        float rotX, rotY, rotZ;       // broadcast icin: Euler kopyasi
        float posX, posY, posZ;
    }

    private final Map<String, Pivot> basePivots = new HashMap<>();
    private final Map<String, ExitPose> exitPoses = new HashMap<>();

    public PoseAnimationPlayer(PoseAnimation animation, float chargeCap) {
        this(animation, chargeCap, DEFAULT_RETURN_DURATION);
    }

    public PoseAnimationPlayer(PoseAnimation animation, float chargeCap, float returnDuration) {
        this.animation = animation;
        this.chargeCap = chargeCap;
        this.returnDuration = returnDuration;
    }

    public Phase getPhase() { return phase; }
    public boolean isActive() { return phase != Phase.IDLE; }

    public void startCharging() {
        phase = Phase.CHARGING;
        phaseStartNanos = System.nanoTime();
        phaseStartElapsed = 0f;
        restorePending = false;
    }

    public void release() {
        if (phase == Phase.CHARGING) {
            phaseStartNanos = System.nanoTime();
            phaseStartElapsed = chargeCap;
            phase = Phase.RELEASING;
        }
    }

    public void reset() {
        phase = Phase.IDLE;
        restorePending = true;
    }

    private float computeElapsed() {
        float dt = (System.nanoTime() - phaseStartNanos) / 1_000_000_000f;
        return phaseStartElapsed + dt;
    }

    private Pivot basePivotOf(String boneName, ModelPart part) {
        Pivot p = basePivots.computeIfAbsent(boneName, k -> new Pivot());
        if (!p.captured) {
            p.x = part.x; p.y = part.y; p.z = part.z;
            p.rx = part.xRot; p.ry = part.yRot; p.rz = part.zRot;
            p.captured = true;
        }
        return p;
    }

    public void applyTo(ModelPart body, ModelPart head, ModelPart rightArm,
                        ModelPart leftArm, ModelPart rightLeg, ModelPart leftLeg) {
        Pivot bodyP = basePivotOf("body", body);
        Pivot headP = basePivotOf("head", head);
        Pivot raP = basePivotOf("rightArm", rightArm);
        Pivot laP = basePivotOf("leftArm", leftArm);
        Pivot rlP = basePivotOf("rightLeg", rightLeg);
        Pivot llP = basePivotOf("leftLeg", leftLeg);

        if (phase == Phase.IDLE) {
            if (restorePending) {
                // Vanilla'nin HIC yazmadigi Euler + pozisyon bilesenlerini tabana cek.
                // (Custom model Euler kopyaliyorsa bilezikler de boylece vanilla'ya doner.)
                body.x = bodyP.x;   body.z = bodyP.z;   body.zRot = bodyP.rz;
                head.x = headP.x;   head.z = headP.z;   head.zRot = headP.rz;
                rightArm.y = raP.y; rightArm.yRot = raP.ry;
                leftArm.y = laP.y;  leftArm.yRot = laP.ry;
                rightLeg.x = rlP.x; rightLeg.yRot = rlP.ry; rightLeg.zRot = rlP.rz;
                leftLeg.x = llP.x;  leftLeg.yRot = llP.ry;  leftLeg.zRot = llP.rz;
                restorePending = false;
            }
            PoseQuatCache.clear(body);  PoseQuatCache.clear(head);
            PoseQuatCache.clear(rightArm); PoseQuatCache.clear(leftArm);
            PoseQuatCache.clear(rightLeg); PoseQuatCache.clear(leftLeg);
            return;
        }

        if (phase == Phase.RETURNING) {
            float t = computeElapsed();
            float weight = smoothstep(1.0f - clamp01(t / returnDuration));
            if (weight <= 0.001f) {
                phase = Phase.IDLE;
                restorePending = true;
                return;
            }
            //            mRX    mRY    mRZ    mPX    mPY    mPZ
            blendOut("body", body, bodyP, weight,     true,  true,  false, false, true,  false);
            blendOut("head", head, headP, weight,     true,  true,  false, false, true,  false);
            blendOut("rightArm", rightArm, raP, weight, true, false, true,  true,  false, true);
            blendOut("leftArm", leftArm, laP, weight,   true, false, true,  true,  false, true);
            blendOut("rightLeg", rightLeg, rlP, weight, true, false, false, false, true,  true);
            blendOut("leftLeg", leftLeg, llP, weight,   true, false, false, false, true,  true);
            return;
        }

        float elapsed;
        if (phase == Phase.CHARGING) {
            elapsed = Math.min(computeElapsed(), chargeCap);
        } else { // RELEASING
            elapsed = computeElapsed();
            if (elapsed >= animation.length()) {
                float end = animation.length();
                captureExitPose("body", animation.sampleBody(end));
                captureExitPose("head", animation.sampleHead(end));
                captureExitPose("rightArm", animation.sampleRightArm(end));
                captureExitPose("leftArm", animation.sampleLeftArm(end));
                captureExitPose("rightLeg", animation.sampleRightLeg(end));
                captureExitPose("leftLeg", animation.sampleLeftLeg(end));
                phase = Phase.RETURNING;
                phaseStartNanos = System.nanoTime();
                phaseStartElapsed = 0f;
                elapsed = end;
            }
        }
        applyBone(animation.sampleBody(elapsed), body, bodyP);
        applyBone(animation.sampleHead(elapsed), head, headP);
        applyBone(animation.sampleRightArm(elapsed), rightArm, raP);
        applyBone(animation.sampleLeftArm(elapsed), leftArm, laP);
        applyBone(animation.sampleRightLeg(elapsed), rightLeg, rlP);
        applyBone(animation.sampleLeftLeg(elapsed), leftLeg, llP);
    }

    private void captureExitPose(String boneName, PoseSample s) {
        ExitPose e = exitPoses.computeIfAbsent(boneName, k -> new ExitPose());
        e.rot = (s.rot() != null) ? new Quaternionf(s.rot()) : null;
        e.rotX = s.rotX(); e.rotY = s.rotY(); e.rotZ = s.rotZ();
        e.posX = s.posX(); e.posY = s.posY(); e.posZ = s.posZ();
    }

    private void blendOut(String boneName, ModelPart part, Pivot base, float weight,
                          boolean mRX, boolean mRY, boolean mRZ,
                          boolean mPX, boolean mPY, boolean mPZ) {
        if (part == null) return;
        ExitPose exit = exitPoses.get(boneName);
        if (exit == null) return;

        if (exit.rot != null) {
            Quaternionf qVanilla = new Quaternionf()
                    .rotationZ(part.zRot).rotateY(part.yRot).rotateX(part.xRot);
            PoseQuatCache.set(part, qVanilla.slerp(exit.rot, weight));
        }
        float tRX = mRX ? part.xRot : base.rx;
        float tRY = mRY ? part.yRot : base.ry;
        float tRZ = mRZ ? part.zRot : base.rz;
        float tPX = mPX ? part.x : base.x;
        float tPY = mPY ? part.y : base.y;
        float tPZ = mPZ ? part.z : base.z;
        part.xRot = lerp(weight, tRX, exit.rotX);
        part.yRot = lerp(weight, tRY, exit.rotY);
        part.zRot = lerp(weight, tRZ, exit.rotZ);
        part.x = lerp(weight, tPX, base.x + exit.posX);
        part.y = lerp(weight, tPY, base.y + exit.posY);
        part.z = lerp(weight, tPZ, base.z + exit.posZ);
    }

    private void applyBone(PoseSample s, ModelPart part, Pivot base) {
        if (part == null) return;
        if (s.rot() != null) {
            PoseQuatCache.set(part, new Quaternionf(s.rot()));
        } else {
            PoseQuatCache.clear(part);
        }
        // Euler broadcast - bilezikler/accessory'ler buradan takip eder.
        part.xRot = s.rotX();
        part.yRot = s.rotY();
        part.zRot = s.rotZ();
        part.x = base.x + s.posX();
        part.y = base.y + s.posY();
        part.z = base.z + s.posZ();
    }

    private static float clamp01(float v) { return v < 0f ? 0f : Math.min(1f, v); }
    private static float smoothstep(float x) { return x * x * (3.0f - 2.0f * x); }
    private static float lerp(float t, float a, float b) { return a + (b - a) * t; }
}