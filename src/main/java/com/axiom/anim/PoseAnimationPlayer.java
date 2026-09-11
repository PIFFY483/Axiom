package com.axiom.anim;

import net.minecraft.client.model.geom.ModelPart;
import org.joml.Quaternionf;
import java.util.HashMap;
import java.util.Map;

public class PoseAnimationPlayer {
    public enum Phase { IDLE, CHARGING, RELEASING, RETURNING }

    public static final float DEFAULT_RETURN_DURATION = 0.25f;

    /** postChargePlayDuration icin sinyal deger: sikistirma yok, animasyon
     *  2.saniyeden sonraki kismini kendi dogal (verideki) hizinda oynar. */
    public static final float NATURAL_PACE = -1f;

    /** Erken birakmada (chargeCap'e ulasmadan) animasyonun ani atlama yapmadan
     *  sarj noktasina (chargeCap) baglanmasi icin gecen sure (saniye). */
    private static final float RELEASE_CATCHUP_DURATION = 0.15f;

    /** Sikistirilmis post-charge animasyonu (2.saniye sonrasi) hedefe daha
     *  ulasilmadan biterse, son karede ek olarak ne kadar daha tutulacagi -
     *  gercek "hedefe varinca birak" sunucu senkronu olmadan basit bir
     *  yaklasim (bkz. class yorumu). */
    private static final float HOLD_AFTER_END = 0.35f;

    private final PoseAnimation animation;
    private final float chargeCap;
    private final float postChargePlayDuration;
    private final float returnDuration;
    private Phase phase = Phase.IDLE;
    private long phaseStartNanos = 0L;
    private float phaseStartElapsed = 0f;
    private boolean restorePending = false;

    /** RELEASING fazinin ilk RELEASE_CATCHUP_DURATION saniyesinde, erken
     *  birakma anindaki gercek elapsed'den chargeCap'e smooth gecis yapar. */
    private boolean earlyReleaseBlend = false;
    private float releaseBlendFromElapsed = 0f;
    private long releaseBlendStartNanos = 0L;

    /** chargeCap'e ulasildigi (dogal ya da erken-blend sonrasi) an - post-charge
     *  (2.saniye sonrasi) kisminin zaman-yeniden-olcekleme referansi. */
    private long postChargeStartNanos = 0L;

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
        this(animation, chargeCap, NATURAL_PACE, DEFAULT_RETURN_DURATION);
    }

    public PoseAnimationPlayer(PoseAnimation animation, float chargeCap, float returnDuration) {
        this(animation, chargeCap, NATURAL_PACE, returnDuration);
    }

    public PoseAnimationPlayer(PoseAnimation animation, float chargeCap, float postChargePlayDuration, float returnDuration) {
        this.animation = animation;
        this.chargeCap = chargeCap;
        this.postChargePlayDuration = postChargePlayDuration;
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
        if (phase != Phase.CHARGING) return;
        beginReleasing(Math.min(computeElapsed(), chargeCap));
    }

    /**
     * CHARGING -> RELEASING geçişini başlatır. chargeElapsedAtTrigger chargeCap'e
     * eşitse (tavana kadar tutuldu) direkt oradan devam eder - snap yok, çünkü
     * zaten o noktadaydık. chargeCap'ten küçükse (erken bırakma) ani atlama
     * yapmadan RELEASE_CATCHUP_DURATION boyunca chargeCap'e smooth blend eder.
     */
    private void beginReleasing(float chargeElapsedAtTrigger) {
        phase = Phase.RELEASING;
        if (chargeElapsedAtTrigger < chargeCap) {
            earlyReleaseBlend = true;
            releaseBlendFromElapsed = chargeElapsedAtTrigger;
            releaseBlendStartNanos = System.nanoTime();
        } else {
            earlyReleaseBlend = false;
            startPostCharge();
        }
    }

    private void startPostCharge() {
        postChargeStartNanos = System.nanoTime();
    }

    /** postChargePlayDuration NATURAL_PACE ise (sinyal), animasyonun kendi dogal
     *  post-charge suresini (length - chargeCap) kullan; degilse sabit sureye sikistir. */
    private float effectivePostChargeDuration() {
        float natural = animation.length() - chargeCap;
        return postChargePlayDuration > 0f ? postChargePlayDuration : natural;
    }

    public void reset() {
        phase = Phase.IDLE;
        restorePending = true;
        earlyReleaseBlend = false;
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
            float chargeElapsed = computeElapsed();
            if (chargeElapsed < chargeCap) {
                elapsed = chargeElapsed;
            } else {
                // Eskiden burada chargeCap'te DURUYORDU (Math.min ile clamp).
                // Artik tavana ulasinca otomatik olarak atilma/yumruk fazina
                // geciyor - tus birakilmasi beklenmiyor.
                beginReleasing(chargeCap);
                elapsed = chargeCap;
            }
        } else { // RELEASING
            if (earlyReleaseBlend) {
                float dt = (System.nanoTime() - releaseBlendStartNanos) / 1_000_000_000f;
                if (dt >= RELEASE_CATCHUP_DURATION) {
                    // Blend bitti - chargeCap'ten itibaren post-charge fazina gec.
                    earlyReleaseBlend = false;
                    startPostCharge();
                    elapsed = chargeCap;
                } else {
                    float frac = smoothstep(clamp01(dt / RELEASE_CATCHUP_DURATION));
                    elapsed = lerp(frac, releaseBlendFromElapsed, chargeCap);
                }
            } else {
                // 2.saniye sonrasi (dash+yumruk) kismi: sabit sureye (ornegin
                // 0.5s) sikistirilmis sekilde oynar, sonra hedefe fiziksel
                // olarak ulasilana kadar (HOLD_AFTER_END kadar ek sure) son
                // karede tutulur - "animasyon bitince direkt vanilla'ya
                // donme" davranisi yerine.
                float duration = effectivePostChargeDuration();
                float dt = (System.nanoTime() - postChargeStartNanos) / 1_000_000_000f;
                float progress = duration <= 1.0e-5f ? 1f : clamp01(dt / duration);
                elapsed = chargeCap + progress * (animation.length() - chargeCap);

                if (dt >= duration + HOLD_AFTER_END) {
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