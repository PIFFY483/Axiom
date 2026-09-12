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

    /**
     * dashDelaySeconds() kadar sonra cagrilir - karakterin kendi atilma/leap darbesi.
     *
     * @param target lockTarget() ile kilitlenen hedef (null olabilir) - dash mesafesini
     *               hedefe olan gercek uzakliga gore sinirlamak icin kullanilir.
     */
    void onDash(ServerPlayer player, float chargeRatio, LivingEntity target);

    /**
     * onDash()'tan hemen sonra, dash penceresi boyunca (impact denemesi
     * baslayana kadar) HER TICK cagrilir. Varsayilani hicbir sey yapmaz -
     * ama tek seferlik bir setDeltaMovement() darbesi Minecraft'in kendi
     * hareket surtunmesi (friction) yuzunden birkac tick icinde sonup
     * gidiyor, yani hesaplanan mesafeye asla ulasilmiyordu. Bu metod, o
     * surtunmeyi her tick tekrar hiz uygulayarak iptal etmek icin var.
     */
    default void tickDash(ServerPlayer player) {}

    /**
     * impactDelaySeconds() kadar sonra, hedef menzile girene ya da
     * impactTimeoutGraceSeconds() dolana kadar HER TICK tekrar denenir.
     *
     * @param forceIfTimedOut true ise ek bekleme suresi de doldu - menzil
     *                        kontrolu yapmadan (ya da yapip yine de) uygula.
     * @return true ise vurus uygulandi (artik tekrar cagrilmaz).
     */
    boolean tryImpact(ServerPlayer player, LivingEntity target, float chargeRatio, boolean forceIfTimedOut);

    /**
     * impactDelaySeconds() kadar sonra, HEDEF OLSUN OLMASIN (hatta hic hedef
     * kilitlenmemis - lockTarget() null donmus - olsa bile) TAM OLARAK BIR
     * KEZ cagrilir.
     *
     * DIKKAT: hedef VARSA bu an, karakterin dash'i fiziksel olarak nereye
     * vardigindan BAGIMSIZ, sabit bir animasyon zamanlamasidir - yani
     * "gercek vurus/iskalama ani" degildir (o an tryImpact()'in donus
     * yaptigi tick'tir). Konum-bagimli efektler (orn. karakterin onunde
     * koni seklinde alan acmak) bu yuzden BURADA degil, tryImpact()
     * icinde, sadece hedef null oldugunda burada tetiklenmelidir - bkz.
     * DashUppercutEffect.onImpactMoment/tryImpact.
     *
     * @param target lockTarget() ile kilitlenen hedef (null olabilir).
     *               Hedef null ise tryImpact() hic cagrilmayacagi icin,
     *               konum-bagimli efektlerin TEK tetiklenme firsati budur.
     */
    default void onImpactMoment(ServerPlayer player, float chargeRatio, LivingEntity target) {}

    /** Karakterin kendi atilma darbesinin, sarj bitisinden kac saniye sonra uygulanacagi. */
    default float dashDelaySeconds() { return 0f; }

    /** Vurus/firlatma denemesinin, sarj bitisinden kac saniye sonra baslayacagi. */
    default float impactDelaySeconds() { return 0f; }

    /** Hedef menzile girmezse, impactDelaySeconds() sonrasinda ne kadar daha beklenecegi. */
    default float impactTimeoutGraceSeconds() { return 0.3f; }
}
