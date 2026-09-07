package com.axiom.anim;

import net.minecraft.client.model.geom.ModelPart;

import java.util.HashMap;
import java.util.Map;

/**
 * Tek bir varlik icin animasyon zamanini yoneten "faz makinesi".
 *
 * FAZLAR:
 *  IDLE      -> hicbir sey yapmiyoruz, vanilla'nin kendi pozu aynen kalir.
 *  CHARGING  -> V basili: zaman 0'dan chargeCap'e ilerler, sonra orada bekler.
 *  RELEASING -> V birakildi: zaman chargeCap'ten animasyonun sonuna kadar oynar.
 *  RETURNING -> Animasyon bitti: son karedeki pozdan, O ANKI TAZE VANILLA
 *               pozuna dogru RETURN_DURATION saniyede yumusakca kayar.
 *               Boylece "son karede takili kalma" veya "aniden sicrama"
 *               olmuyor - ne zaman baslarsa baslasin dogru vanilla pozuna
 *               duzgunce inip IDLE'a geciyor.
 *
 * PIVOT NOTU: "position" kanali bind pivotuna gore delta'dir. Her kemigin
 * gercek pivotunu ilk kullanimda yakalayip (basePivots) hep ona gore
 * calisiyoruz: aktifken base+delta, donuste base+weight*delta.
 */
public class AxiomAnimationPlayer {

    public enum Phase { IDLE, CHARGING, RELEASING, RETURNING }

    private static final float RETURN_DURATION = 0.25f; // saniye - son pozdan vanilla'ya kayma suresi

    private static final class Pivot {
        float x, y, z;
        boolean captured = false;
    }

    /** Animasyonun bittigi andaki poz - RETURNING fazinin "baslangic" ucu. */
    private static final class BonePose {
        float rotX, rotY, rotZ;   // radyan
        float posDeltaX, posDeltaY, posDeltaZ; // bind pivotuna gore delta
    }

    private final String animationName;
    private final float chargeCap; // saniye

    private Phase phase = Phase.IDLE;
    private long phaseStartNanos = 0L;
    private float phaseStartElapsed = 0f;

    private final Map<String, Pivot> basePivots = new HashMap<>();
    private final Map<String, BonePose> exitPoses = new HashMap<>();

    public AxiomAnimationPlayer(String animationName, float chargeCap) {
        this.animationName = animationName;
        this.chargeCap = chargeCap;
    }

    public Phase getPhase() {
        return phase;
    }

    public boolean isActive() {
        return phase != Phase.IDLE;
    }

    public void startCharging() {
        phase = Phase.CHARGING;
        phaseStartNanos = System.nanoTime();
        phaseStartElapsed = 0f;
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
    }

    private float computeElapsed() {
        float dt = (System.nanoTime() - phaseStartNanos) / 1_000_000_000f;
        return phaseStartElapsed + dt;
    }

    private Pivot basePivotOf(String boneName, ModelPart part) {
        Pivot p = basePivots.computeIfAbsent(boneName, k -> new Pivot());
        if (!p.captured) {
            p.x = part.x; p.y = part.y; p.z = part.z;
            p.captured = true;
        }
        return p;
    }

    public void applyTo(ModelPart body, ModelPart head, ModelPart rightArm,
                        ModelPart leftArm, ModelPart rightLeg, ModelPart leftLeg) {

        // Pivotlari her zaman yakala (ilk cagrida gercek degerleri kaydetmek icin).
        Pivot bodyP = basePivotOf("body", body);
        Pivot headP = basePivotOf("head", head);
        Pivot raP   = basePivotOf("rightArm", rightArm);
        Pivot laP   = basePivotOf("leftArm", leftArm);
        Pivot rlP   = basePivotOf("rightLeg", rightLeg);
        Pivot llP   = basePivotOf("leftLeg", leftLeg);

        if (phase == Phase.IDLE) {
            return; // hicbir seye dokunma, vanilla neyse o kalsin
        }

        if (phase == Phase.RETURNING) {
            float t = computeElapsed(); // 0 -> RETURN_DURATION
            float weight = 1.0f - clamp01(t / RETURN_DURATION); // 1 -> 0
            weight = smoothstep(weight);

            if (weight <= 0.001f) {
                phase = Phase.IDLE;
                return; // bu karede dokunma, vanilla zaten dogru
            }

            blendOut("body", body, bodyP, weight);
            blendOut("head", head, headP, weight);
            blendOut("rightArm", rightArm, raP, weight);
            blendOut("leftArm", leftArm, laP, weight);
            blendOut("rightLeg", rightLeg, rlP, weight);
            blendOut("leftLeg", leftLeg, llP, weight);
            return;
        }

        BBAnimation anim = AxiomAnimationRegistry.INSTANCE.get(animationName);
        if (anim == null) { phase = Phase.IDLE; return; }

        float elapsed;
        if (phase == Phase.CHARGING) {
            elapsed = Math.min(computeElapsed(), chargeCap);
        } else { // RELEASING
            elapsed = computeElapsed();
            if (elapsed >= anim.length) {
                // Animasyon bitti: son karedeki pozu kaydet, RETURNING'e gec.
                captureExitPose("body", anim.bone("body"), anim.length);
                captureExitPose("head", anim.bone("head"), anim.length);
                captureExitPose("rightArm", anim.bone("rightArm"), anim.length);
                captureExitPose("leftArm", anim.bone("leftArm"), anim.length);
                captureExitPose("rightLeg", anim.bone("rightLeg"), anim.length);
                captureExitPose("leftLeg", anim.bone("leftLeg"), anim.length);

                phase = Phase.RETURNING;
                phaseStartNanos = System.nanoTime();
                phaseStartElapsed = 0f;
                return; // bu karede zaten son animasyon karesi gosterilmisti, dokunma
            }
        }

        applyBone(anim.bone("body"), body, bodyP, elapsed);
        applyBone(anim.bone("head"), head, headP, elapsed);
        applyBone(anim.bone("rightArm"), rightArm, raP, elapsed);
        applyBone(anim.bone("leftArm"), leftArm, laP, elapsed);
        applyBone(anim.bone("rightLeg"), rightLeg, rlP, elapsed);
        applyBone(anim.bone("leftLeg"), leftLeg, llP, elapsed);
    }

    private void captureExitPose(String boneName, BBBoneAnimation boneAnim, float atTime) {
        BonePose pose = exitPoses.computeIfAbsent(boneName, k -> new BonePose());
        pose.rotX = pose.rotY = pose.rotZ = 0f;
        pose.posDeltaX = pose.posDeltaY = pose.posDeltaZ = 0f;
        if (boneAnim == null) return;

        if (!boneAnim.rotation.isEmpty()) {
            BBVector r = boneAnim.rotation.sample(atTime);
            pose.rotX = (float) Math.toRadians(r.x);
            pose.rotY = (float) Math.toRadians(r.y);
            pose.rotZ = (float) Math.toRadians(r.z);
        }
        if (!boneAnim.position.isEmpty()) {
            BBVector p = boneAnim.position.sample(atTime);
            pose.posDeltaX = p.x;
            pose.posDeltaY = p.y;
            pose.posDeltaZ = p.z;
        }
    }

    /** weight=1 -> tam animasyon son karesi, weight=0 -> o anki taze vanilla pozu. */
    private void blendOut(String boneName, ModelPart part, Pivot base, float weight) {
        if (part == null) return;
        BonePose exit = exitPoses.get(boneName);
        if (exit == null) return;

        // part.xRot/yRot/zRot su an HENUZ DOKUNULMAMIS -> taze vanilla degeri.
        float vanillaRotX = part.xRot, vanillaRotY = part.yRot, vanillaRotZ = part.zRot;
        part.xRot = lerp(weight, vanillaRotX, exit.rotX);
        part.yRot = lerp(weight, vanillaRotY, exit.rotY);
        part.zRot = lerp(weight, vanillaRotZ, exit.rotZ);

        // Pozisyon: weight=0 -> tam base (delta=0), weight=1 -> base+exitDelta.
        part.x = base.x + weight * exit.posDeltaX;
        part.y = base.y + weight * exit.posDeltaY;
        part.z = base.z + weight * exit.posDeltaZ;
    }

    private void applyBone(BBBoneAnimation boneAnim, ModelPart part, Pivot base, float elapsed) {
        if (part == null) return;

        if (boneAnim != null && !boneAnim.rotation.isEmpty()) {
            BBVector r = boneAnim.rotation.sample(elapsed);
            part.xRot = (float) Math.toRadians(r.x);
            part.yRot = (float) Math.toRadians(r.y);
            part.zRot = (float) Math.toRadians(r.z);
        }

        if (boneAnim != null && !boneAnim.position.isEmpty()) {
            BBVector p = boneAnim.position.sample(elapsed);
            part.x = base.x + p.x;
            part.y = base.y + p.y;
            part.z = base.z + p.z;
        } else {
            part.x = base.x;
            part.y = base.y;
            part.z = base.z;
        }
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : Math.min(v, 1f);
    }

    private static float smoothstep(float x) {
        return x * x * (3.0f - 2.0f * x);
    }

    private static float lerp(float t, float a, float b) {
        return a + (b - a) * t;
    }
}