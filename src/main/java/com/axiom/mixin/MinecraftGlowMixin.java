package com.axiom.mixin;

import com.axiom.client.ClientTargetHighlight;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla'nin outline-render kararini verdigi asil yer burasi:
 * LevelRenderer, her entity icin bufferSource secerken
 * Minecraft#shouldEntityAppearGlowing(Entity)'i cagirir, o da normalde
 * sadece entity.isCurrentlyGlowing()'e bakar (spectator hedefi haric).
 *
 * Biz TargetLockPacket ile gelen id'leri entity data'ya hic dokunmadan
 * (bkz. ClientTargetHighlight javadoc) direkt burada "evet, glow" diye
 * zorluyoruz - boylece Axiom'un kendi hedef vurgusu vanilla'nin glow
 * potion efekti/spectator mekanizmasiyla hic cakismiyor.
 */
@Mixin(Minecraft.class)
public class MinecraftGlowMixin {

    @Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true, require = 0)
    private void axiom$forceHighlightGlow(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (ClientTargetHighlight.isHighlighted(entity.getId())) {
            cir.setReturnValue(true);
        }
    }
}
