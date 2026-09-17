package com.axiom.client;

/**
 * Client-only: skill HAREKET (WASD/zipla) kilidi su an aktif mi. Bakis
 * yonu/fare kilidiyle KARISTIRMA - o SkillLookLockClientState'te, cunku
 * ikisi farkli anlarda baslar (bkz. SkillLookLockClientState javadoc).
 *
 * bkz. SkillMovementLockPacket (bunu ac/kapa yapan paket) ve
 * MovementLockClientHandler (bu state'i asil WASD engellemesine ceviren yer).
 */
public final class SkillMovementLockClientState {
    private SkillMovementLockClientState() {}

    private static volatile boolean locked = false;

    public static boolean isLocked() {
        return locked;
    }

    public static void lock() {
        locked = true;
    }

    public static void unlock() {
        locked = false;
    }
}
