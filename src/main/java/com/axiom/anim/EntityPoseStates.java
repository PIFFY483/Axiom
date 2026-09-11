package com.axiom.anim;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Eski ClientChargeStates'in yerini alir. Farki: ClientChargeStates tek bir
 * sabit ANIMATION_NAME'e gore entityId -> AxiomAnimationPlayer tutuyordu
 * (yani ayni anda sadece TEK bir poz oynatilabiliyordu). Bu sinif ise
 * entityId -> (pozAdi -> PoseAnimationPlayer) tutar, yani:
 *
 *  - Ayni entity ayni anda birden fazla pozu tasiyabilir (her biri kendi
 *    poseId'sinde izole).
 *  - Farkli entity'ler (PvP'de A ve B oyuncusu) birbirinin state'ini asla
 *    gormez/degistirmez - cunku her ikisinin kendi entityId anahtari altinda
 *    tamamen ayri Map'i var.
 *
 * poseId ornekleri: "vacuumUppercut", "vacuumCharge", "powerfulPunch"...
 */
public final class EntityPoseStates {

    private EntityPoseStates() {}

    private static final Map<Integer, Map<String, PoseAnimationPlayer>> STATES = new ConcurrentHashMap<>();

    private static Map<String, PoseAnimationPlayer> playersOf(int entityId) {
        return STATES.computeIfAbsent(entityId, id -> new ConcurrentHashMap<>());
    }

    /** Var olan player'i dondurur, yoksa null. */
    public static PoseAnimationPlayer get(int entityId, String poseId) {
        Map<String, PoseAnimationPlayer> byPose = STATES.get(entityId);
        return byPose == null ? null : byPose.get(poseId);
    }

    /** O entity icin kayitli TUM pozlari dondurur - mixin'in genel apply dongusu bunu kullanir. */
    public static Map<String, PoseAnimationPlayer> getAll(int entityId) {
        Map<String, PoseAnimationPlayer> byPose = STATES.get(entityId);
        return byPose == null ? Map.of() : byPose;
    }

    /** Sarj/oynatma baslat. Player yoksa otomatik olusturur (varsayilan 0.25s donus suresi). */
    public static void startCharging(int entityId, String poseId, PoseAnimation animation, float chargeCap) {
        playersOf(entityId)
                .computeIfAbsent(poseId, id -> new PoseAnimationPlayer(animation, chargeCap))
                .startCharging();
    }

    /** Ayni, ama vanilla'ya donus suresini de sen belirlersin. */
    public static void startCharging(int entityId, String poseId, PoseAnimation animation,
                                      float chargeCap, float returnDuration) {
        playersOf(entityId)
                .computeIfAbsent(poseId, id -> new PoseAnimationPlayer(animation, chargeCap, returnDuration))
                .startCharging();
    }

    /** Sikistirilmis post-charge oynatma suresi + donus suresi ikisi birden. */
    public static void startCharging(int entityId, String poseId, PoseAnimation animation,
                                      float chargeCap, float postChargePlayDuration, float returnDuration) {
        playersOf(entityId)
                .computeIfAbsent(poseId, id -> new PoseAnimationPlayer(animation, chargeCap, postChargePlayDuration, returnDuration))
                .startCharging();
    }

    public static void release(int entityId, String poseId) {
        Map<String, PoseAnimationPlayer> byPose = STATES.get(entityId);
        if (byPose == null) return;
        PoseAnimationPlayer player = byPose.get(poseId);
        if (player != null) player.release();
    }

    public static void reset(int entityId, String poseId) {
        Map<String, PoseAnimationPlayer> byPose = STATES.get(entityId);
        if (byPose == null) return;
        PoseAnimationPlayer player = byPose.get(poseId);
        if (player != null) player.reset();
    }

    /**
     * Oyuncu dunyadan ayrilinca / entity unload olunca CAGIRMAYI UNUTMA -
     * aksi halde harita sonsuza kadar buyur (memory leak).
     */
    public static void remove(int entityId) {
        STATES.remove(entityId);
    }

    public static void clear() {
        STATES.clear();
    }
}
