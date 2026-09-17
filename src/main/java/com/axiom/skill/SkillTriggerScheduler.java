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
 * ChargeSessionManager, sarj fazi bitince (CHARGE_PHASE_END'e ulasma ya da
 * erken birakma) buraya devreder. Bu sinif efekti HEMEN degil, animasyondaki
 * "dash" ve "impact" anlarina denk gelecek sekilde GECIKMELI tetikler
 * (bkz. SkillEffect.dashDelaySeconds() / impactDelaySeconds()).
 *
 * Hedef, CHARGE_PHASE_END anında (schedule() cagrildiginda) BIR KEZ kilitlenir
 * - dash/impact ikisi de bu hedefi kullanir.
 */
public final class SkillTriggerScheduler {
    private SkillTriggerScheduler() {}

    private record Key(UUID playerId, String skillId) {}

    private static final class Pending {
        final Skill skill;
        final float chargeRatio;
        final LivingEntity target; // null olabilir - dash yine olur, firlatma atlanir
        final long dashAtGameTime;
        final long impactAtGameTime;
        final long impactTimeoutGameTime;
        boolean dashDone = false;
        boolean impactMomentFired = false; // onImpactMoment() TAM BIR KEZ cagrilsin diye

        Pending(Skill skill, float chargeRatio, LivingEntity target,
                long dashAtGameTime, long impactAtGameTime, long impactTimeoutGameTime) {
            this.skill = skill;
            this.chargeRatio = chargeRatio;
            this.target = target;
            this.dashAtGameTime = dashAtGameTime;
            this.impactAtGameTime = impactAtGameTime;
            this.impactTimeoutGameTime = impactTimeoutGameTime;
        }
    }

    private static final Map<Key, Pending> PENDING = new ConcurrentHashMap<>();

    public static void schedule(ServerPlayer player, Skill skill, float chargeRatio) {
        SkillEffect effect = skill.effect();
        if (effect == null) return;

        LivingEntity target = effect.lockTarget(player);
        long now = player.level().getGameTime();
        long dashAt = now + Math.round(effect.dashDelaySeconds() * 20.0f);
        long impactAt = now + Math.round(effect.impactDelaySeconds() * 20.0f);
        long impactTimeout = impactAt + Math.round(effect.impactTimeoutGraceSeconds() * 20.0f);

        PENDING.put(new Key(player.getUUID(), skill.id()),
                new Pending(skill, chargeRatio, target, dashAt, impactAt, impactTimeout));

        // Hedef simdi kilitlendi (dash daha baslamadan) - client'ta kirmizi
        // highlight gostermesi icin bildir.
        if (target != null) {
            ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> target),
                    new TargetLockPacket(target.getId(), true));
        }
    }

    private static void clearHighlight(Pending p) {
        if (p.target != null) {
            ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p.target),
                    new TargetLockPacket(p.target.getId(), false));
        }
    }

    /** Her sunucu tick'inde bir kez cagrilir. */
    public static void tickAll(MinecraftServer server) {
        if (PENDING.isEmpty()) return;
        PENDING.entrySet().removeIf(entry -> {
            Pending p = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey().playerId());
            if (player == null) return true;

            long now = player.level().getGameTime();

            if (!p.dashDone && now >= p.dashAtGameTime) {
                p.skill.effect().onDash(player, p.chargeRatio, p.target);
                p.dashDone = true;
                // Bakis yonu/fare kilidi SADECE dash basladiginda devreye
                // giriyor - hareket (WASD) kilidi bundan AYRI, sarj
                // basladiginda zaten devrede (bkz. ChargeInputPacket).
                if (p.skill.effect().locksMovementAndLook()) {
                    SkillLookLockManager.acquire(player);
                }
            }

            if (p.dashDone) {
                p.skill.effect().tickDash(player);
            }

            if (now >= p.impactAtGameTime) {
                // onImpactMoment() hedef olsun olmasin TAM OLARAK BIR KEZ (impact
                // penceresine ilk girildiginde) cagrilir. Hedef VARSA bu artik sadece
                // bir "haber verme" - konum-bagimli efektler bunu DEGIL, asagidaki
                // tryImpact()'in gercekten hit/miss donduğu ani kullanmali (bkz.
                // SkillEffect.onImpactMoment javadoc'u).
                if (!p.impactMomentFired) {
                    p.impactMomentFired = true;
                    p.skill.effect().onImpactMoment(player, p.chargeRatio, p.target);
                }

                if (p.target == null) {
                    // Firlatilacak hedef yoktu, is bitti - hareket VE bakis
                    // kilidi burada aciliyor (ikisi de - bakis kilidi henuz
                    // acquire edilmemis bile olabilir, release() bu durumda
                    // sessizce hicbir sey yapmaz).
                    if (p.skill.effect().locksMovementAndLook()) {
                        SkillMovementLockManager.release(player);
                        SkillLookLockManager.release(player);
                    }
                    return true;
                }

                boolean timedOut = now >= p.impactTimeoutGameTime;
                boolean done = p.skill.effect().tryImpact(player, p.target, p.chargeRatio, timedOut);
                if (done || timedOut) {
                    clearHighlight(p); // hit de olsa miss de olsa highlight kapanir
                    // Skill tamamen bitti (isabet ya da timeout) - hareket VE
                    // bakis kilidi burada aciliyor.
                    if (p.skill.effect().locksMovementAndLook()) {
                        SkillMovementLockManager.release(player);
                        SkillLookLockManager.release(player);
                    }
                    return true;
                }
                return false;
            }
            return false;
        });
    }

    public static void clearPlayer(ServerPlayer player) {
        PENDING.keySet().removeIf(k -> k.playerId().equals(player.getUUID()));
    }
}
