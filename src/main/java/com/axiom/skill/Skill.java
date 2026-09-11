package com.axiom.skill;

import com.axiom.anim.PoseAnimation;
import com.axiom.anim.PoseAnimationPlayer;
import net.minecraft.resources.ResourceLocation;

/**
 * Bir skill'in tanimi: hangi id ile taninacagi (network + poseId + slot
 * atamalarinda kullanilir), menude gorunecek isim, oynatacagi PoseAnimation,
 * sarj tavani (chargeCap - CHARGING fazinin ne kadar surdugu),
 * post-charge oynatma suresi (postChargePlayDuration - 2.saniye sonrasinin
 * gercek zamanda ne kadar surede oynatilacagi; PoseAnimationPlayer.NATURAL_PACE
 * = sikistirma yok) ve opsiyonel sunucu-taraf oyun ici etki (effect).
 *
 * icon simdilik opsiyonel: texture'in yoksa null birak, SkillMenuScreen
 * otomatik olarak isim baslangicini kutu icine yazar. Ileride texture
 * eklediginde sadece bu alani doldurman yeterli.
 */
public record Skill(String id, String displayName, PoseAnimation animation, float chargeCap,
                     float postChargePlayDuration, ResourceLocation icon, SkillEffect effect) {

    public Skill(String id, String displayName, PoseAnimation animation, float chargeCap) {
        this(id, displayName, animation, chargeCap, PoseAnimationPlayer.NATURAL_PACE, null, null);
    }

    public Skill(String id, String displayName, PoseAnimation animation, float chargeCap, ResourceLocation icon) {
        this(id, displayName, animation, chargeCap, PoseAnimationPlayer.NATURAL_PACE, icon, null);
    }

    /** Sikistirilmis post-charge oynatma suresi + sunucu-taraf etkisi olan skill'ler icin. */
    public Skill(String id, String displayName, PoseAnimation animation, float chargeCap,
                 float postChargePlayDuration, SkillEffect effect) {
        this(id, displayName, animation, chargeCap, postChargePlayDuration, null, effect);
    }
}
