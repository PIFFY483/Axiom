package com.axiom.network;

import com.axiom.client.camera.FovController;
import com.axiom.client.camera.HitStopController;
import com.axiom.client.camera.ScreenShakeController;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> Client: Dash Uppercut skill'inin gorsel anlarinda (leap / impact)
 * kamera fx sistemlerini (FOV punch, screen shake, hitstop) tetikler.
 *
 * Neden ayri bir packet: FovController/ScreenShakeController/HitStopController
 * client-only static state - sunucu (DashUppercutEffect.onDash/tryImpact)
 * bunlara dogrudan erisemez, o yuzden DashPhaseSyncPacket ornegindeki gibi
 * ilgili oyuncuya kucuk bir "hangi an oldu" paketi gonderiyoruz.
 *
 * YENI: "power" (0-1, DashUppercutEffect.powerOf(chargeRatio) ile ayni deger)
 * eklendi - kisa/yakin sicramalarda efektler zaten yeterliydi ama uzak/tam
 * sarjli sicramalarda "sonuk" kaliyorlardi. Artik FOV punch ve screen shake
 * sarj oranina gore olcekleniyor - tam sarjda cok daha carpici.
 */
public class DashUppercutFxPacket {

    public enum Stage {
        /** Karakter atilma (leap) anina girdi - DashUppercutEffect.onDash(). */
        LEAP,
        /** Yumruk hedefe isabet etti - DashUppercutEffect.tryImpact() basarili donus. */
        IMPACT
    }

    private final Stage stage;
    private final float power; // 0-1, sarj oranindan (DashUppercutEffect.powerOf) geliyor

    public DashUppercutFxPacket(Stage stage, float power) {
        this.stage = stage;
        this.power = power;
    }

    public static void encode(DashUppercutFxPacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.stage);
        buf.writeFloat(msg.power);
    }

    public static DashUppercutFxPacket decode(FriendlyByteBuf buf) {
        Stage stage = buf.readEnum(Stage.class);
        float power = buf.readFloat();
        return new DashUppercutFxPacket(stage, power);
    }

    public static void handle(DashUppercutFxPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            switch (msg.stage) {
                case LEAP -> onLeap(msg.power);
                case IMPACT -> onImpact(msg.power);
            }
        }));
        ctx.setPacketHandled(true);
    }

    // Sarj oranina gore FOV punch buyuklugu/suresi - az sarjda hafif "whoosh",
    // tam sarjda cok daha belirgin bir genisleme.
    private static final float LEAP_FOV_MIN = 8.0f;
    private static final float LEAP_FOV_MAX = 30.0f;
    private static final float LEAP_DURATION_MIN = 0.14f;
    private static final float LEAP_DURATION_MAX = 0.26f;

    /** Ziplama/atilma anindaki hiz hissi - kisa ve belirgin bir FOV genislemesi. */
    private static void onLeap(float power) {
        float amount = lerp(power, LEAP_FOV_MIN, LEAP_FOV_MAX);
        float duration = lerp(power, LEAP_DURATION_MIN, LEAP_DURATION_MAX);
        FovController.addPunch(amount, duration);
    }

    // Sarj oranina gore ekran sarsintisi guc/sure ve hitstop suresi - az
    // sarjda "light/heavyImpact" arasi, tam sarjda "explosion/bossSlam"
    // seviyesini asan bir carpicilikta.
    private static final float IMPACT_SHAKE_POWER_MIN = 0.20f;
    private static final float IMPACT_SHAKE_POWER_MAX = 0.70f;
    private static final float IMPACT_SHAKE_DURATION_MIN = 0.18f;
    private static final float IMPACT_SHAKE_DURATION_MAX = 0.40f;
    private static final float IMPACT_HITSTOP_SALISE_MIN = 3.0f;  // 3*50ms = 150ms
    private static final float IMPACT_HITSTOP_SALISE_MAX = 10.0f; // 10*50ms = 500ms

    // YENI: vurus aninda da FOV punch - "isabet" hissini gorsel olarak
    // guclendiriyor, leap'ten biraz daha kisa/keskin bir spike.
    private static final float IMPACT_FOV_MIN = 10.0f;
    private static final float IMPACT_FOV_MAX = 26.0f;
    private static final float IMPACT_FOV_DURATION_MIN = 0.12f;
    private static final float IMPACT_FOV_DURATION_MAX = 0.20f;

    private static void onImpact(float power) {
        float shakePower = lerp(power, IMPACT_SHAKE_POWER_MIN, IMPACT_SHAKE_POWER_MAX);
        float shakeDuration = lerp(power, IMPACT_SHAKE_DURATION_MIN, IMPACT_SHAKE_DURATION_MAX);
        ScreenShakeController.addShake(shakePower, shakeDuration);

        float hitstop = lerp(power, IMPACT_HITSTOP_SALISE_MIN, IMPACT_HITSTOP_SALISE_MAX);
        HitStopController.request(hitstop);

        float fovAmount = lerp(power, IMPACT_FOV_MIN, IMPACT_FOV_MAX);
        float fovDuration = lerp(power, IMPACT_FOV_DURATION_MIN, IMPACT_FOV_DURATION_MAX);
        FovController.addPunch(fovAmount, fovDuration);
    }

    private static float lerp(float t, float a, float b) { return a + (b - a) * t; }
}
