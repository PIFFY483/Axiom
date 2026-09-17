package com.axiom.skill;

import com.axiom.network.ModNetwork;
import com.axiom.network.SkillLookLockPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SkillMovementLockManager'in (WASD kilidi, sarj basindan itibaren aktif)
 * ESI ama BILEREK AYRI: bu sinif SADECE dash basladiginda (bkz.
 * SkillTriggerScheduler.tickAll'daki onDash cagri noktasi) acquire edilir -
 * sarj (charge) asamasinda oyuncu serbestce bakabilmeli, bakis/fare kilidi
 * mantiksiz olurdu. Release, hareket kilidiyle AYNI anda (skill tamamen
 * bitince) olur.
 *
 * REFCOUNT KULLANILIYOR: bkz. SkillMovementLockManager'daki ayni gerekce.
 */
public final class SkillLookLockManager {
    private SkillLookLockManager() {}

    private static final Map<UUID, Integer> LOCK_COUNTS = new ConcurrentHashMap<>();

    public static void acquire(ServerPlayer player) {
        int count = LOCK_COUNTS.merge(player.getUUID(), 1, Integer::sum);
        if (count == 1) send(player, true);
    }

    public static void release(ServerPlayer player) {
        LOCK_COUNTS.computeIfPresent(player.getUUID(), (id, count) -> {
            if (count <= 1) {
                send(player, false);
                return null;
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
                new SkillLookLockPacket(locked));
    }
}
