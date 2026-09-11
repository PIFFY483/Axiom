package com.axiom.skill;

import com.axiom.anim.pose.DashUppercutPose;
import com.axiom.skill.effect.DashUppercutEffect;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Butun skillerin tek merkezi listesi. Client hem server bu sinifi kullanir
 * (server tarafinda gercek animasyon oynatilmaz ama skillId'nin GECERLI
 * olup olmadigini dogrulamak icin ayni id listesine ihtiyac var).
 *
 * YENI SKILL EKLEME ADIMLARI:
 *  1. com.axiom.anim.pose altina ExamplePose'a benzer yeni bir poz sinifi yaz
 *     (PoseAnimation'i implement eder).
 *  2. Asagidaki static bloga bir satir ekle:
 *     register(new Skill("benim_skillim", "Benim Skillim", BenimPozum.INSTANCE, BenimPozum.CHARGE_PHASE_END));
 *  3. Hepsi bu - menude otomatik cikar, slota surukleyip birakinca calisir.
 *     (Ikon eklemek istersen Skill(...) constructor'ina ResourceLocation ver.)
 */
public final class SkillRegistry {

    private SkillRegistry() {}

    private static final Map<String, Skill> SKILLS = new LinkedHashMap<>();

    static {
        register(new Skill("dash_uppercut", "Dash Uppercut", DashUppercutPose.INSTANCE,
                DashUppercutPose.CHARGE_PHASE_END, DashUppercutPose.POST_CHARGE_PLAY_DURATION,
                DashUppercutEffect.INSTANCE));
    }

    public static void register(Skill skill) {
        SKILLS.put(skill.id(), skill);
    }

    /** Bilinmeyen id icin null doner - cagiran taraf null kontrolu yapmali. */
    public static Skill get(String id) {
        return SKILLS.get(id);
    }

    public static boolean exists(String id) {
        return SKILLS.containsKey(id);
    }

    /** Menude gosterilecek sirali skill listesi. */
    public static Collection<Skill> all() {
        return SKILLS.values();
    }
}
