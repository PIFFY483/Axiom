package com.axiom.network;

import com.axiom.client.fx.WindTrailFxRenderer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> Client (TRACKING_ENTITY_AND_SELF): dash sirasinda (ucus/leap
 * fazi boyunca) karakterin etrafinda beliren, geriye dogru akan yari-saydam
 * ruzgar cizgileri efektini baslatir.
 *
 * DashShockwaveFxPacket'ten farkli olarak bu TEK SEFERLIK bir patlama degil,
 * dash'in TUM suresi (travelSeconds) boyunca her frame'de karakterin GUNCEL
 * pozisyonunu takip eden surekli bir efekttir - bu yuzden sabit bir
 * spawn-noktasi degil, entity ID tasir (bkz. WindTrailFxRenderer.spawnFlightTrail).
 */
public class DashWindTrailFxPacket {
    private final int entityId;
    private final float dirX, dirZ;
    private final float power;
    private final float durationSeconds;

    public DashWindTrailFxPacket(int entityId, float dirX, float dirZ, float power, float durationSeconds) {
        this.entityId = entityId;
        this.dirX = dirX;
        this.dirZ = dirZ;
        this.power = power;
        this.durationSeconds = durationSeconds;
    }

    public static void encode(DashWindTrailFxPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeFloat(msg.dirX);
        buf.writeFloat(msg.dirZ);
        buf.writeFloat(msg.power);
        buf.writeFloat(msg.durationSeconds);
    }

    public static DashWindTrailFxPacket decode(FriendlyByteBuf buf) {
        int entityId = buf.readVarInt();
        float dirX = buf.readFloat();
        float dirZ = buf.readFloat();
        float power = buf.readFloat();
        float durationSeconds = buf.readFloat();
        return new DashWindTrailFxPacket(entityId, dirX, dirZ, power, durationSeconds);
    }

    public static void handle(DashWindTrailFxPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                WindTrailFxRenderer.spawnFlightTrail(msg.entityId, msg.dirX, msg.dirZ, msg.power, msg.durationSeconds)));
        ctx.setPacketHandled(true);
    }
}
