package com.axiom.client.camera;

import com.axiom.Axiom;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * FovController / ScreenShakeController'in hesapladigi degerleri GERCEK
 * kameraya uygulayan yer burasi.
 *
 * BILEREK MIXIN KULLANMIYORUZ: Forge, tam bu is icin ViewportEvent'i
 * saglıyor (GameRenderer#getFov ve Camera#setRotation'a private mixin
 * atmak yerine). Bu hem obfuscation/mapping'e bagimli olmadigi icin daha
 * saglam, hem de Forge tarafindan resmi olarak desteklenen yol.
 *
 * ONEMLI SINIRLAMA: Forge'un kamera event'leri kamerayi DONDURMEYE
 * (yaw/pitch/roll) ve FOV'u degistirmeye izin veriyor, ama kamerayi
 * pozisyon olarak ÖTELEMEYE (translate) izin vermiyor - bunun icin
 * (Forge gelistiricilerinin de dogruladigi gibi) oyuncunun gercek eye
 * pozisyonunu degistirmek gerekir ki bu screen-shake icin uygun degil.
 * Bu yuzden ScreenShakeController'in offsetX/offsetY'si (orijinalde
 * "blok" cinsinden dusunulmus) burada KUCUK bir yaw/pitch titremesine
 * cevriliyor - gorsel olarak sarsinti hissi ayni, sadece pozisyon yerine
 * rotasyon uzerinden calisiyor.
 */
@Mod.EventBusSubscriber(modid = Axiom.MOD_ID, value = Dist.CLIENT)
public class CameraFxApplier {

    // offsetX/offsetY (blok, max ~0.35) -> derece donusum katsayisi.
    // Kucuk tutuldu ki shake "ekran titremesi" gibi hissettirsin,
    // "kamera sarhos" gibi degil.
    private static final float SHAKE_BLOCKS_TO_DEGREES = 18.0f;

    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        double newFov = FovController.calculateFov(event.getFOV(), (float) event.getPartialTick());
        event.setFOV(newFov);
    }

    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (!ScreenShakeController.isActive()) return;

        float yawShake = ScreenShakeController.getOffsetX() * SHAKE_BLOCKS_TO_DEGREES;
        float pitchShake = ScreenShakeController.getOffsetY() * SHAKE_BLOCKS_TO_DEGREES;
        float roll = ScreenShakeController.getRollOffset();

        event.setYaw(event.getYaw() + yawShake);
        event.setPitch(event.getPitch() + pitchShake);
        event.setRoll(event.getRoll() + roll);
    }
}
