package com.axiom.client;

/**
 * Client-only: skill BAKIS YONU/FARE kilidi su an aktif mi ve kilitlendigi
 * an yakalanan aci (yRot/xRot) ne.
 *
 * SkillMovementLockClientState'ten (WASD kilidi) BILEREK AYRI: ikisi FARKLI
 * anlarda baslar - hareket kilidi sarj basladigi anda devreye girer, bakis
 * kilidi ise SADECE dash basladiginda (sarj sirasinda oyuncu serbestce
 * bakabilmeli, fare/bakis kilitli olursa sarj sirasinda hedef secmek/nisan
 * almak saçma olurdu). Ikisi de ayni anda (skill tamamen bitince) aciliyor.
 *
 * bkz. SkillLookLockPacket (ac/kapa paketi), MouseHandlerMixin (fare
 * girdisini kaynaginda engelleyen yer) ve ClientTickHandler (tick-sonu
 * sigorta olarak yRot/xRot'u bu aciya geri donduren yer).
 */
public final class SkillLookLockClientState {
    private SkillLookLockClientState() {}

    private static volatile boolean locked = false;
    private static volatile float lockedYaw = 0f;
    private static volatile float lockedPitch = 0f;

    public static boolean isLocked() {
        return locked;
    }

    public static float getLockedYaw() {
        return lockedYaw;
    }

    public static float getLockedPitch() {
        return lockedPitch;
    }

    public static void lock(float yaw, float pitch) {
        lockedYaw = yaw;
        lockedPitch = pitch;
        locked = true;
    }

    public static void unlock() {
        locked = false;
    }
}
