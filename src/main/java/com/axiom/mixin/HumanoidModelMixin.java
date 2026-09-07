package com.axiom.mixin;

import com.axiom.anim.EntityPoseStates;
import com.axiom.anim.PoseAnimationPlayer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * Bu sinif SADECE pozlari oynatir - hicbir pozun kendine ozgu (hardcoded)
 * mantigini icermez. Eskiden tek bir ClientChargeStates.get(entityId) player'i
 * cagiriyordu (tek poz); artik EntityPoseStates.getAll(entityId) ile o entity
 * icin aktif olan HER pozu bulup sirayla uyguluyor.
 *
 * Yeni bir poz eklemek istedigin zaman BU DOSYAYA DOKUNMAN GEREKMEZ:
 *   1. Yeni pozunu PoseAnimation'i implement eden kendi .java dosyana yaz.
 *   2. Server -> client sync paketinde
 *      EntityPoseStates.startCharging(entityId, "yeniPoz", YeniPoz.INSTANCE, chargeCap)
 *      ve EntityPoseStates.release(entityId, "yeniPoz") cagir.
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin<T extends LivingEntity> {

    @Shadow @Final public ModelPart head;
    @Shadow @Final public ModelPart hat;
    @Shadow @Final public ModelPart body;
    @Shadow @Final public ModelPart rightArm;
    @Shadow @Final public ModelPart leftArm;
    @Shadow @Final public ModelPart rightLeg;
    @Shadow @Final public ModelPart leftLeg;

    @Inject(method = "setupAnim", at = @At("TAIL"))
    private void axiom$applyPoses(
            T entity,
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch,
            CallbackInfo ci) {

        Map<String, PoseAnimationPlayer> active = EntityPoseStates.getAll(entity.getId());
        if (active.isEmpty()) return;

        // Birden fazla poz ayni anda aktifse, sirayla uygulanir - her biri
        // bir oncekinin uzerine biner.
        for (PoseAnimationPlayer player : active.values()) {
            // isActive() kontrolu YOK: IDLE'daki player da applyTo icinde
            // restorePending temizligini yapabiliyor olmali.
            player.applyTo(body, head, rightArm, leftArm, rightLeg, leftLeg);
        }
    }
}
