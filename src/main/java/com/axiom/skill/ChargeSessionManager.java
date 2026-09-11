package com.axiom.skill;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sunucu tarafinda, oyuncu+skill basina sarj baslangic zamanini (game tick)
 * tutar. IKI ayri noktadan CHARGE_PHASE_END'e ulasildigini tespit eder -
 * PoseAnimationPlayer'daki client-taraf "chargeCap'te durmuyor, otomatik
 * post-charge fazina geciyor" davranisiyla ayni anlara denk gelir:
 *
 *  1) Oyuncu tusu erken birakirsa (chargeCap'e ulasmadan) -> releaseEarly()
 *     o anki sarj oranina gore hemen SkillTriggerScheduler'a devreder.
 *  2) Tus birakilmadan sarj tavanina (chargeCap) ulasilirsa -> her sunucu
 *     tick'inde tickAll() kontrol edip tam guc (1.0) ile devreder.
 *
 * Not: burada hemen "efekt calisti" DEMEK DEGIL - asil dash/impact anlari
 * SkillTriggerScheduler'da, animasyondaki gorsel anlarla eslesecek sekilde
 * GECIKMELI olarak tetiklenir.
 */
public final class ChargeSessionManager {
    private ChargeSessionManager() {}

    private record Key(UUID playerId, String skillId) {}
    private record Session(long startGameTime) {}

    private static final Map<Key, Session> SESSIONS = new ConcurrentHashMap<>();

    public static void startCharging(ServerPlayer player, String skillId, long gameTime) {
        SESSIONS.put(new Key(player.getUUID(), skillId), new Session(gameTime));
    }

    /** Tus, sarj tavanina ulasmadan birakildiginda cagrilir. */
    public static void releaseEarly(ServerPlayer player, Skill skill, long gameTime) {
        Session session = SESSIONS.remove(new Key(player.getUUID(), skill.id()));
        if (session == null || skill.effect() == null) return;

        long capTicks = capTicks(skill);
        float ratio = capTicks <= 0 ? 1f : Math.min(1f, (gameTime - session.startGameTime()) / (float) capTicks);
        SkillTriggerScheduler.schedule(player, skill, ratio);
    }

    /** Her sunucu tick'inde bir kez cagrilir: tavana ulasan ama hala tutulan sarjlari otomatik devreder. */
    public static void tickAll(MinecraftServer server) {
        if (SESSIONS.isEmpty()) return;
        SESSIONS.entrySet().removeIf(entry -> {
            Key key = entry.getKey();
            Skill skill = SkillRegistry.get(key.skillId());
            if (skill == null) return true; // gecersiz skillId, temizle

            ServerPlayer player = server.getPlayerList().getPlayer(key.playerId());
            if (player == null) return true; // oyuncu artik cevrimdisi

            long capTicks = capTicks(skill);
            if (player.level().getGameTime() - entry.getValue().startGameTime() >= capTicks) {
                if (skill.effect() != null) SkillTriggerScheduler.schedule(player, skill, 1.0f);
                return true;
            }
            return false;
        });
    }

    /** Oyuncu dunyadan ayrilinca cagrilmali - aksi halde harita sonsuza kadar buyur. */
    public static void clearPlayer(ServerPlayer player) {
        SESSIONS.keySet().removeIf(k -> k.playerId().equals(player.getUUID()));
        SkillTriggerScheduler.clearPlayer(player);
    }

    private static long capTicks(Skill skill) {
        return Math.round(skill.chargeCap() * 20.0f);
    }
}
