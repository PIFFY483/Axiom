package com.axiom.network;

import com.axiom.anim.ClientChargeStates;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> Client: bir varligin sarj (charge) durumu degisti. */
public class ChargeSyncPacket {
    public final int entityId;
    public final boolean charging;
    public final long stateStartGameTime;

    public ChargeSyncPacket(int entityId, boolean charging, long stateStartGameTime) {
        this.entityId = entityId;
        this.charging = charging;
        this.stateStartGameTime = stateStartGameTime;
    }

    public static void encode(ChargeSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeBoolean(msg.charging);
        buf.writeVarLong(msg.stateStartGameTime);
    }

    public static ChargeSyncPacket decode(FriendlyByteBuf buf) {
        return new ChargeSyncPacket(buf.readVarInt(), buf.readBoolean(), buf.readVarLong());
    }

    public static void handle(ChargeSyncPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            System.out.println("[Axiom] Client sync aldi: entityId=" + msg.entityId + " charging=" + msg.charging);
            if (msg.charging) {
                ClientChargeStates.INSTANCE.startCharging(msg.entityId);
            } else {
                ClientChargeStates.INSTANCE.release(msg.entityId);
            }
        }));
        ctx.setPacketHandled(true);
    }
}
