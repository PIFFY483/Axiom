package com.axiom.skill;

import com.axiom.network.ModNetwork;
import com.axiom.network.SkillMovementLockPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sarj basladigindan (ChargeInputPacket pressed=true, bkz. cagri noktasi)
 * skill tamamen bitene (SkillTriggerScheduler.tickAll'da Pending kaldirilana)
 * kadar oyuncunun hareket/bakis kilidini yonetir.
 *
 * GERCEK KILITLEME CLIENT TARAFINDA olur (bkz. MovementLockClientHandler'in
 * WASD/zipla girdisini sifirlamasi ve ClientTickHandler'in yRot/xRot'u
 * dondurmesi) - bu sinif sadece DOGRU ANDA ac/kapa paketini gonderir.
 *
 * REFCOUNT KULLANILIYOR: teorik olarak oyuncu ust uste birden fazla
 * skill/slot sarjini baslatabilir (ornegin R basiliyken G'ye de basmak) -
 * kilit SADECE hicbiri kalmayinca acilir, aksi halde birinin bitmesi
 * digerinin ortasinda kilidi yanlislikla acabilirdi.
 */
public final class SkillMovementLockManager {
    private SkillMovementLockManager() {}

    private static final Map<UUID, Integer> LOCK_COUNTS = new ConcurrentHashMap<>();

    public static void acquire(ServerPlayer player) {
        int count = LOCK_COUNTS.merge(player.getUUID(), 1, Integer::sum);
        if (count == 1) send(player, true);
    }

    public static void release(ServerPlayer player) {
        LOCK_COUNTS.computeIfPresent(player.getUUID(), (id, count) -> {
            if (count <= 1) {
                send(player, false);
                return null; // haritadan tamamen cikar
            }
            return count - 1;
        });
    }

    /** Oyuncu dunyadan ayrilinca CAGIRMAYI UNUTMA - aksi halde kilit sunucu haritasinda sonsuza kadar kalir. */
    public static void clearPlayer(ServerPlayer player) {
        if (LOCK_COUNTS.remove(player.getUUID()) != null) {
            send(player, false);
        }
    }

    private static void send(ServerPlayer player, boolean locked) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new SkillMovementLockPacket(locked));
    }
}
