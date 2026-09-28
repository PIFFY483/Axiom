package com.axiom.skill;

import com.axiom.network.ModNetwork;
import com.axiom.network.TargetLockPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.network.PacketDistributor;

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
 *
 * YENI - CANLI HEDEF ONIZLEMESI: eskiden hedef SADECE sarj bitince
 * (SkillTriggerScheduler.schedule() icinde) TEK SEFER kilitlenip client'a
 * bildiriliyordu - yani oyuncu sarj SIRASINDA hangi mobun hedeflenecegini
 * hic goremiyordu. Simdi her tick'te (sarj devam ederken) lockTarget()
 * tekrar cagriliyor; aday hedef degisirse eski glow kapatilip yenisi
 * acilyor. Sarj bitince SkillTriggerScheduler kendi lockTarget() cagrisini
 * yine yapar (nihai/otoriter secim odur) - bu sadece gorsel bir onizleme.
 */
public final class ChargeSessionManager {
    private ChargeSessionManager() {}

    private record Key(UUID playerId, String skillId) {}

    private static final class Session {
        final long startGameTime;
        LivingEntity highlightedTarget; // su an client'a "kilitli" diye bildirilen onizleme hedefi

        Session(long startGameTime) {
            this.startGameTime = startGameTime;
        }
    }

    private static final Map<Key, Session> SESSIONS = new ConcurrentHashMap<>();

    public static void startCharging(ServerPlayer player, String skillId, long gameTime) {
        SESSIONS.put(new Key(player.getUUID(), skillId), new Session(gameTime));
    }

    /** Tus, sarj tavanina ulasmadan birakildiginda cagrilir. */
    public static void releaseEarly(ServerPlayer player, Skill skill, long gameTime) {
        Session session = SESSIONS.remove(new Key(player.getUUID(), skill.id()));
        if (session == null) return;
        clearPreview(session);
        if (skill.effect() == null) return;

        long capTicks = capTicks(skill);
        float ratio = capTicks <= 0 ? 1f : Math.min(1f, (gameTime - session.startGameTime) / (float) capTicks);
        SkillTriggerScheduler.schedule(player, skill, ratio);
    }

    /** Her sunucu tick'inde bir kez cagrilir: tavana ulasan ama hala tutulan sarjlari otomatik devreder. */
    public static void tickAll(MinecraftServer server) {
        if (SESSIONS.isEmpty()) return;
        SESSIONS.entrySet().removeIf(entry -> {
            Key key = entry.getKey();
            Session session = entry.getValue();
            Skill skill = SkillRegistry.get(key.skillId());
            if (skill == null) { clearPreview(session); return true; } // gecersiz skillId, temizle

            ServerPlayer player = server.getPlayerList().getPlayer(key.playerId());
            if (player == null) return true; // oyuncu artik cevrimdisi (highlight zaten client'la beraber gidiyor)

            if (skill.effect() != null) {
                updatePreview(player, skill, session);
            }

            long capTicks = capTicks(skill);
            if (player.level().getGameTime() - session.startGameTime >= capTicks) {
                clearPreview(session);
                if (skill.effect() != null) SkillTriggerScheduler.schedule(player, skill, 1.0f);
                return true;
            }
            return false;
        });
    }

    /** Sarj devam ederken her tick cagrilir - aday hedef degistiyse glow'u client'ta gunceller. */
    private static void updatePreview(ServerPlayer player, Skill skill, Session session) {
        LivingEntity candidate = skill.effect().lockTarget(player);
        if (candidate == session.highlightedTarget) return; // degisiklik yok, paket gonderme

        if (session.highlightedTarget != null) {
            LivingEntity old = session.highlightedTarget;
            ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> old),
                    new TargetLockPacket(old.getId(), false));
        }
        if (candidate != null) {
            ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> candidate),
                    new TargetLockPacket(candidate.getId(), true));
        }
        session.highlightedTarget = candidate;
    }

    private static void clearPreview(Session session) {
        if (session.highlightedTarget != null) {
            LivingEntity old = session.highlightedTarget;
            ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> old),
                    new TargetLockPacket(old.getId(), false));
            session.highlightedTarget = null;
        }
    }

    /** Oyuncu dunyadan ayrilinca cagrilmali - aksi halde harita sonsuza kadar buyur. */
    public static void clearPlayer(ServerPlayer player) {
        SESSIONS.entrySet().removeIf(entry -> {
            if (!entry.getKey().playerId().equals(player.getUUID())) return false;
            clearPreview(entry.getValue());
            return true;
        });
        SkillTriggerScheduler.clearPlayer(player);
    }

    private static long capTicks(Skill skill) {
        return Math.round(skill.chargeCap() * 20.0f);
    }
}