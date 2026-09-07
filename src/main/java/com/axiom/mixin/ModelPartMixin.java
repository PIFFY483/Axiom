package com.axiom.mixin;

import com.axiom.anim.PoseQuatCache;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelPart.class)
public abstract class ModelPartMixin {
    @Shadow public float x;
    @Shadow public float y;
    @Shadow public float z;

    @Inject(method = "translateAndRotate", at = @At("HEAD"), cancellable = true)
    private void axiom$applyWorldQuat(PoseStack pose, CallbackInfo ci) {
        Quaternionf q = PoseQuatCache.get((ModelPart) (Object) this);
        if (q == null) return; // vanilla Euler yolu aynen devam
        pose.translate(x / 16.0F, y / 16.0F, z / 16.0F);
        // MC surumune gore: 1.20.5+ joml PoseStack:
        pose.mulPose(q);
        // 1.20.1-1.20.4 (com.mojang.math) kullanirsan ust satiri buna cevir:
        // pose.mulPose(new com.mojang.math.Quaternionf(q.x(), q.y(), q.z(), q.w()));
        ci.cancel();
    }
}