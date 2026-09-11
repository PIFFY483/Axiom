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
 */
public class DashUppercutFxPacket {

    public enum Stage {
        /** Karakter atilma (leap) anina girdi - DashUppercutEffect.onDash(). */
        LEAP,
        /** Yumruk hedefe isabet etti - DashUppercutEffect.tryImpact() basarili donus. */
        IMPACT
    }

    private final Stage stage;

    public DashUppercutFxPacket(Stage stage) {
        this.stage = stage;
    }

    public static void encode(DashUppercutFxPacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.stage);
    }

    public static DashUppercutFxPacket decode(FriendlyByteBuf buf) {
        return new DashUppercutFxPacket(buf.readEnum(Stage.class));
    }

    public static void handle(DashUppercutFxPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            switch (msg.stage) {
                case LEAP -> onLeap();
                case IMPACT -> onImpact();
            }
        }));
        ctx.setPacketHandled(true);
    }

    /** Ziplama/atilma anindaki hiz hissi - kisa ve belirgin bir FOV genislemesi. */
    private static void onLeap() {
        FovController.addPunch(14.0f, 0.18f);
    }

    /** Vurus ani - ekran sarsintisi + hitstop (0.2s = 4 salise, istenen deger). */
    private static final float IMPACT_HITSTOP_SALISE = 4.0f; // 4 * 50ms = 200ms = 0.2s

    private static void onImpact() {
        ScreenShakeController.heavyImpact();
        HitStopController.request(IMPACT_HITSTOP_SALISE);
    }
}
