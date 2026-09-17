package com.axiom.network;

import com.axiom.client.fx.WindTrailFxRenderer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> Client (TRACKING_ENTITY_AND_SELF): Dash Uppercut'in yumruk anindaki
 * (isabet ETSIN, hedef menzile hic girmeden ISKALANSIN, ya da bastan hic
 * HEDEF OLMASIN - HANGISI OLURSA OLSUN) yukari dogru yukselen ruzgar
 * tuylerini baslatir.
 *
 * Sabit bir koordinat DEGIL, entityId tasir: WindTrailFxRenderer.spawnVacuumEmitter
 * bu ID'den 2.5 saniye boyunca PERIYODIK olarak karakterin O ANKI (guncel)
 * pozisyon+bakisini yeniden ornekleyip yeni tuy ciftleri doguruyor - boylece
 * "bosa yumrukta efekt karakterin arkasinda/yanlis yerden cikiyor" sorunu
 * (animasyon RETURNING fazina gecerken TEK bir aninin yakalanmasindan
 * kaynaklaniyordu) ortadan kalkiyor: karakter yerine oturunca sonraki
 * pulse'lar hep dogru noktadan cikar.
 *
 * PunchShockwaveFxPacket'ten BAGIMSIZ ve bilerek AYRI: o sadece GERCEK
 * isabet aninda (metal sok dalgasiyla birlikte) gonderilir, bu paket ise
 * DashUppercutEffect'in yumruk-sonucu belli olan UC noktasinda da (tryImpact
 * hit dali, tryImpact miss-timeout dali, onImpactMoment target==null dali)
 * gonderilir.
 */
public class VacuumWindFxPacket {
    private final int entityId;
    private final float power;

    public VacuumWindFxPacket(int entityId, float power) {
        this.entityId = entityId;
        this.power = power;
    }

    public static void encode(VacuumWindFxPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeFloat(msg.power);
    }

    public static VacuumWindFxPacket decode(FriendlyByteBuf buf) {
        return new VacuumWindFxPacket(buf.readVarInt(), buf.readFloat());
    }

    public static void handle(VacuumWindFxPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                WindTrailFxRenderer.spawnVacuumEmitter(msg.entityId, msg.power)));
        ctx.setPacketHandled(true);
    }
}
