package com.axiom.skill;

import com.axiom.anim.PoseAnimation;
import net.minecraft.resources.ResourceLocation;

/**
 * Bir skill'in tanimi: hangi id ile taninacagi (network + poseId + slot
 * atamalarinda kullanilir), menude gorunecek isim, oynatacagi PoseAnimation
 * ve sarj tavani (chargeCap - PoseAnimationPlayer'in CHARGING fazinin ne
 * kadar surdugu).
 *
 * icon simdilik opsiyonel: texture'in yoksa null birak, SkillMenuScreen
 * otomatik olarak isim baslangicini kutu icine yazar. Ileride texture
 * eklediginde sadece bu alani doldurman yeterli.
 */
public record Skill(String id, String displayName, PoseAnimation animation, float chargeCap, ResourceLocation icon) {

    public Skill(String id, String displayName, PoseAnimation animation, float chargeCap) {
        this(id, displayName, animation, chargeCap, null);
    }
}
