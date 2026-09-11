package com.axiom.skill;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Skill'in sarj fazi bitince (CHARGE_PHASE_END'e ulasilinca ya da erken
 * birakilinca) sunucu tarafinda calisacak IKI ASAMALI oyun ici etki.
 *
 * Iki asamaya ayrilmasinin sebebi: animasyondaki "atilma" (leap/dash) ve
 * "vurus/firlatma" (impact) anlari FARKLI zamanlarda gerceklesiyor (bkz.
 * DashUppercutPose.DASH_TRIGGER_TIME ve animasyon uzunlugu/LENGTH) - efekt
 * bu gorsel anlarla es zamanli tetiklenmeli, hepsi charge bitince tek
 * seferde degil. Zamanlamayi SkillTriggerScheduler yonetir (gecikmeleri
 * bu arayuzdeki dashDelaySeconds()/impactDelaySeconds()'tan okur).
 */
public interface SkillEffect {

    /** CHARGE_PHASE_END anında BIR KEZ cagrilir - hedefi kilitler (bulunamazsa null). */
    LivingEntity lockTarget(ServerPlayer player);

    /** dashDelaySeconds() kadar sonra cagrilir - karakterin kendi atilma/leap darbesi. */
    void onDash(ServerPlayer player, float chargeRatio);

    /**
     * impactDelaySeconds() kadar sonra, hedef menzile girene ya da
     * impactTimeoutGraceSeconds() dolana kadar HER TICK tekrar denenir.
     *
     * @param forceIfTimedOut true ise ek bekleme suresi de doldu - menzil
     *                        kontrolu yapmadan (ya da yapip yine de) uygula.
     * @return true ise vurus uygulandi (artik tekrar cagrilmaz).
     */
    boolean tryImpact(ServerPlayer player, LivingEntity target, float chargeRatio, boolean forceIfTimedOut);

    /** Karakterin kendi atilma darbesinin, sarj bitisinden kac saniye sonra uygulanacagi. */
    default float dashDelaySeconds() { return 0f; }

    /** Vurus/firlatma denemesinin, sarj bitisinden kac saniye sonra baslayacagi. */
    default float impactDelaySeconds() { return 0f; }

    /** Hedef menzile girmezse, impactDelaySeconds() sonrasinda ne kadar daha beklenecegi. */
    default float impactTimeoutGraceSeconds() { return 0.3f; }
}
