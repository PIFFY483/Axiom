package com.axiom.anim.pose;

import com.axiom.anim.PoseAnimation;
import com.axiom.anim.PoseSample;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class DashUppercutPose implements PoseAnimation {
    public static final DashUppercutPose INSTANCE = new DashUppercutPose();
    private DashUppercutPose() {}

    private static final float LENGTH = 3.0f;
    public static final float CHARGE_PHASE_END = 2.0f;

    /** Karakterin one atildigi (leap) animasyon-zamani ani (2. saniyede DEGIL, 2.2'de). */
    public static final float DASH_TRIGGER_TIME = 2.2f;

    /** 2. saniyeden sonraki (dash+yumruk) kismin oyunda GERCEK zamanda oynatilma
     *  suresi. Animasyon verisinde bu kisim 1 saniye (2.0 -> LENGTH=3.0), ama
     *  her zaman bu sabit sureye sikistirilir/hizlandirilir - hedefe fiilen
     *  ulasma isi animasyon suresine degil, sarja gore olcek olan fiziksel
     *  atilma hizina birakilir (bkz. DashUppercutEffect). */
    public static final float POST_CHARGE_PLAY_DURATION = 0.5f;

    /** TEST ANAHTARI: vurus ters yone donuyorsa / bacaklar one aciliyorsa true yap. */
    private static final boolean FLIP_YAW = false;

    // ==== BIND PIVOT'LARI (Minecraft model uzayi, y-ASAGI, piksel) ====
    private static final Vector3f PIVOT_ROOT = new Vector3f(0, 24, 0);
    private static final Vector3f PIVOT_BODY = new Vector3f(0, 0, 0);
    private static final Vector3f PIVOT_HEAD = new Vector3f(0, 0, 0);
    private static final Vector3f PIVOT_RARM = new Vector3f(-5, 2, 0);
    private static final Vector3f PIVOT_LARM = new Vector3f(5, 2, 0);
    private static final Vector3f PIVOT_RLEG = new Vector3f(-1.9f, 12, 0);
    private static final Vector3f PIVOT_LLEG = new Vector3f(1.9f, 12, 0);

    // ---- root ----
    private static final float[][] ROOT_ROT = {
            {0.0f, 0f, 0f, 0f}, {2.4f, 0f, -7.5f, 0f},
            {2.55f, 1.0836f, -72.4897f, -3.5124f}, {2.85f, 1.0836f, -72.4897f, -3.5124f},
            {2.95f, 1.0836f, -72.4897f, -3.5124f}, {3.0f, 1.0836f, -72.4897f, -3.5124f},
    };
    private static final float[][] ROOT_POS = {
            {0.0f, 0f, 0f, 0f}, {0.3f, 0f, -2f, 0f}, {0.5f, 0f, -3f, 0f}, {0.75f, 0f, -4f, 0f},
            {1.0f, 0f, -5f, 0f}, {1.8f, 0f, -5.5f, 0f}, {2.0f, 0f, -5.5f, 0f}, {2.2f, 0f, -5.5f, 0f},
            {2.4f, 0f, -5.5f, 0f}, {2.55f, 0f, -5.5f, 0f}, {2.85f, 0f, -5.5f, 0f},
            {2.95f, 0f, -5.5f, 0f}, {3.0f, 0f, -5.5f, 0f},
    };
    // ---- body ----
    private static final float[][] BODY_ROT = {
            {0.0f, 0f, 0f, 0f}, {0.3f, 7.5f, 0f, 0f}, {0.5f, 12.5462f, 4.8812f, 1.0848f},
            {0.75f, 22.5462f, 4.8812f, 1.0848f}, {1.0f, 28.0126f, 11.6648f, 4.3194f},
            {1.8f, 33.2799f, 13.8693f, 5.5285f}, {2.0f, 38.2799f, 13.8693f, 5.5285f},
            {2.2f, 38.2799f, 13.8693f, 5.5285f}, {2.4f, 37.351f, 7.5454f, 0.539f},
            {2.55f, 14.3993f, 5.1584f, 3.232f}, {2.85f, 3.3993f, 5.1584f, 3.232f},
            {2.95f, 3.3993f, 5.1584f, 3.232f}, {3.0f, 3.3993f, 5.1584f, 3.232f},
    };
    private static final float[][] BODY_POS = {
            {0.0f, 0f, 0f, 0f}, {0.3f, 0f, 0f, 0f}, {0.5f, 0f, 0f, 0f}, {0.75f, 0f, 0f, 0f},
            {1.0f, 0f, 0f, 0f}, {1.8f, 0f, 0f, 0f}, {2.0f, 0f, 0f, 0f}, {2.2f, 0f, 0f, 0f},
            {2.4f, -2f, 4f, -5f}, {2.55f, -2f, 5.1f, -5f}, {2.8f, -2f, 5.5f, -5f},
            {2.85f, -2f, 5.1f, -5f}, {2.95f, -2f, 5.1f, -5f}, {3.0f, -2f, 5.1f, -5f},
    };
    // ---- head ----
    private static final float[][] HEAD_ROT = {
            {0.0f, 0f, 0f, 0f}, {0.75f, -2.5f, 0f, 0f}, {1.0f, -2.5f, 0f, 0f}, {1.8f, 5f, 0f, 0f},
            {2.0f, 5f, 0f, 0f}, {2.2f, 5f, 0f, 0f}, {2.4f, 5f, 0f, 0f},
            {2.6f, 5.4704f, 23.903f, 2.2222f}, {2.85f, 6.7144f, 41.804f, 4.4871f},
            {2.95f, 6.7144f, 41.804f, 4.4871f}, {3.0f, 6.7144f, 41.804f, 4.4871f},
    };
    // ---- rightArm ----
    private static final float[][] RIGHTARM_ROT = {
            {0.0f, 0f, 0f, 0f}, {0.3f, 9.5458f, -2.9932f, 17.2501f}, {0.5f, 21.5108f, -5.7793f, 24.2347f},
            {0.75f, 30.9797f, -8.374f, 28.542f}, {1.0f, 34.8397f, -12.7241f, 34.7584f},
            {1.8f, 38.8528f, -15.8955f, 38.7484f}, {2.0f, 16.4131f, -2.9242f, 23.2805f},
            {2.2f, 39.5016f, -2.3575f, 21.3604f}, {2.4f, -0.1862f, -2.4024f, 13.854f},
            {2.6f, -94.6985f, 51.8372f, 16.6721f}, {2.7f, -132.7614f, 73.0545f, 0.3056f},
    };
    private static final float[][] RIGHTARM_POS = {
            {0.0f, 0f, 0f, 0f}, {2.6f, -0.5f, -0.3f, 2f}, {2.7f, -1.1f, -0.3f, 0.2f},
    };
    // ---- leftArm ----
    private static final float[][] LEFTARM_ROT = {
            {0.0f, 0f, 0f, 0f}, {0.3f, -15f, 0f, 0f}, {0.5f, -26.1423f, -3.2408f, -13.7531f},
            {0.75f, -29.9678f, -9.2291f, -24.7828f}, {1.0f, -49.2308f, -12.4175f, -28.7047f},
            {1.8f, -56.4011f, -14.5039f, -30.121f}, {2.0f, -69.1664f, -5.9265f, -42.6384f},
            {2.2f, -82.1664f, -5.9265f, -42.6384f}, {2.4f, -25.7156f, 4.8504f, -25.0663f},
            {2.6f, -25.7156f, 4.8504f, -25.0663f}, {2.85f, 12.6396f, 4.9008f, -15.0299f},
            {2.95f, 12.6396f, 4.9008f, -15.0299f}, {3.0f, 12.6396f, 4.9008f, -15.0299f},
    };
    // ---- rightLeg ----
    private static final float[][] RIGHTLEG_ROT = {
            {0.0f, 0f, 0f, 0f}, {0.3f, 9.9627f, -0.8672f, 4.9244f}, {0.5f, 27.0406f, -4.2544f, 14.3442f},
            {0.75f, 34.5406f, -4.2544f, 14.3442f}, {1.0f, 39.37f, -5.8432f, 16.282f},
            {1.8f, 38.8644f, -8.999f, 20.1936f}, {2.0f, 38.8644f, -8.999f, 20.1936f},
            {2.2f, 39.1942f, -7.1096f, 17.84f}, {2.4f, 32.1942f, -7.1096f, 17.84f},
            {2.6f, -14.2616f, -11.22f, 34.9449f}, {2.85f, 2.8796f, -15.0538f, 16.8774f},
            {2.95f, 2.8796f, -15.0538f, 16.8774f}, {3.0f, 2.8796f, -15.0538f, 16.8774f},
    };
    private static final float[][] RIGHTLEG_POS = {
            {0.0f, 0f, 0f, 0f}, {0.3f, 0f, 1f, 2f}, {0.5f, 0f, 1f, 2f}, {0.75f, 0f, 1f, 4f},
            {1.0f, 0f, 1.5f, 6.1f}, {1.8f, 0f, 2f, 7f}, {2.0f, 0f, 3f, 8f}, {2.2f, 0f, 3f, 8f},
            {2.4f, -2.1f, 6.4f, 0.8f}, {2.6f, -3.2f, 5.7f, -1.7f}, {2.85f, -2.4f, 5f, -3.7f},
            {2.95f, -2.4f, 5f, -3.7f}, {3.0f, -2.4f, 5f, -3.7f},
    };
    // ---- leftLeg ----
    private static final float[][] LEFTLEG_ROT = {
            {0.0f, 0f, 0f, 0f}, {0.3f, -12.4885f, -0.5409f, -2.4408f}, {0.5f, -24.7793f, -3.0905f, -9.4831f},
            {0.75f, -19.7793f, -3.0905f, -9.4831f}, {1.0f, -14.7793f, -3.0905f, -9.4831f},
            {1.8f, -17.1349f, -3.8302f, -11.8756f}, {2.0f, -17.1349f, -3.8302f, -11.8756f},
            {2.2f, -11.1349f, -3.8302f, -11.8756f}, {2.4f, 16.3651f, -3.8302f, -11.8756f},
            {2.55f, 13.4741f, -7.8241f, -19.3989f}, {2.6f, 16.6717f, -11.501f, -14.169f},
            {2.85f, -1.3283f, -11.501f, -14.169f}, {2.95f, -1.3283f, -11.501f, -14.169f},
            {3.0f, -1.3283f, -11.501f, -14.169f},
    };
    private static final float[][] LEFTLEG_POS = {
            {0.0f, 0f, 0f, 0f}, {0.3f, 0f, 1f, 0f}, {0.5f, 0f, 1.4f, 1f}, {0.75f, 0f, 3f, 1f},
            {1.0f, 0f, 4f, 1f}, {1.8f, 0f, 4.3f, 1f}, {2.0f, 0f, 4.9f, 0.7f}, {2.2f, 0f, 4.9f, 0.7f},
            {2.4f, -1f, 5.9f, 0.7f}, {2.6f, -1.8f, 4.9f, -2.3f}, {2.85f, -1.8f, 4.9f, -4.1f},
            {2.95f, -1.8f, 4.9f, -4.1f}, {3.0f, -1.8f, 4.9f, -4.1f},
    };

    private static float lerp(float t, float a, float b) { return a + (b - a) * t; }

    private static float[] sampleTrack(float[][] track, float t) {
        if (track.length == 0) return new float[]{0, 0, 0};
        if (t <= track[0][0]) return new float[]{track[0][1], track[0][2], track[0][3]};
        int last = track.length - 1;
        if (t >= track[last][0]) return new float[]{track[last][1], track[last][2], track[last][3]};
        for (int i = 0; i < last; i++) {
            float t0 = track[i][0], t1 = track[i + 1][0];
            if (t >= t0 && t <= t1) {
                float d = (t1 - t0) <= 1.0e-5f ? 0f : (t - t0) / (t1 - t0);
                return new float[]{lerp(d, track[i][1], track[i + 1][1]),
                        lerp(d, track[i][2], track[i + 1][2]),
                        lerp(d, track[i][3], track[i + 1][3])};
            }
        }
        return new float[]{track[last][1], track[last][2], track[last][3]};
    }

    private static Quaternionf localRotQuat(float[][] rotTrack, float t) {
        float[] r = sampleTrack(rotTrack, t);
        float ry = (float) Math.toRadians(r[1]);
        if (FLIP_YAW) ry = -ry;
        return new Quaternionf()
                .rotationZ((float) Math.toRadians(r[2]))
                .rotateY(ry)
                .rotateX((float) Math.toRadians(r[0]));
    }

    /** BB y-up -> MC y-down. */
    private static Vector3f samplePosMC(float[][] posTrack, float t) {
        if (posTrack == null) return new Vector3f(0f);
        float[] p = sampleTrack(posTrack, t);
        return new Vector3f(p[0], -p[1], p[2]);
    }

    private static final class WT {
        final Quaternionf rot; final Vector3f pos;
        WT(Quaternionf r, Vector3f p) { rot = r; pos = p; }
    }

    private static WT worldRoot(float t) {
        return new WT(localRotQuat(ROOT_ROT, t),
                new Vector3f(PIVOT_ROOT).add(samplePosMC(ROOT_POS, t)));
    }

    private static WT worldBody(float t) {
        WT root = worldRoot(t);
        Vector3f local = new Vector3f(PIVOT_BODY).sub(PIVOT_ROOT).add(samplePosMC(BODY_POS, t));
        return new WT(new Quaternionf(root.rot).mul(localRotQuat(BODY_ROT, t)),
                new Vector3f(root.pos).add(root.rot.transform(local)));
    }

    /** Bacaklar dahil HER child icin: parent zinciri BB Outliner'daki gibi.
     *  head/rightArm/leftArm -> body ; rightLeg/leftLeg -> waist(identity) -> root. */
    private static PoseSample child(WT parent, Vector3f parentPivot, Vector3f childPivot,
                                    float[][] rotTrack, float[][] posTrack, float t) {
        Vector3f local = new Vector3f(childPivot).sub(parentPivot);
        if (posTrack != null) local.add(samplePosMC(posTrack, t));
        Quaternionf wr = new Quaternionf(parent.rot).mul(localRotQuat(rotTrack, t));
        Vector3f wp = new Vector3f(parent.pos).add(parent.rot.transform(local));
        Vector3f disp = new Vector3f(wp).sub(childPivot);
        Vector3f e = new Vector3f();
        wr.getEulerAnglesZYX(e);
        return new PoseSample(e.x, e.y, e.z, disp.x, disp.y, disp.z, new Quaternionf(wr));
    }

    private static PoseSample fromWT(WT w, Vector3f pivot) {
        Vector3f e = new Vector3f();
        w.rot.getEulerAnglesZYX(e);
        Vector3f d = new Vector3f(w.pos).sub(pivot);
        return new PoseSample(e.x, e.y, e.z, d.x, d.y, d.z, new Quaternionf(w.rot));
    }

    @Override public float length() { return LENGTH; }

    @Override public PoseSample sampleBody(float t) { return fromWT(worldBody(t), PIVOT_BODY); }

    @Override public PoseSample sampleHead(float t) {
        return child(worldBody(t), PIVOT_BODY, PIVOT_HEAD, HEAD_ROT, null, t);
    }
    @Override public PoseSample sampleRightArm(float t) {
        return child(worldBody(t), PIVOT_BODY, PIVOT_RARM, RIGHTARM_ROT, RIGHTARM_POS, t);
    }
    @Override public PoseSample sampleLeftArm(float t) {
        return child(worldBody(t), PIVOT_BODY, PIVOT_LARM, LEFTARM_ROT, null, t);
    }
    @Override public PoseSample sampleRightLeg(float t) {
        return child(worldRoot(t), PIVOT_ROOT, PIVOT_RLEG, RIGHTLEG_ROT, RIGHTLEG_POS, t);
    }
    @Override public PoseSample sampleLeftLeg(float t) {
        return child(worldRoot(t), PIVOT_ROOT, PIVOT_LLEG, LEFTLEG_ROT, LEFTLEG_POS, t);
    }
}