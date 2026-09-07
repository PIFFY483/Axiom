package com.axiom.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/** Client -> Server: oyuncu V tusuna basti/birakti. */
public class ChargeInputPacket {
    public final boolean pressed;

    public ChargeInputPacket(boolean pressed) {
        this.pressed = pressed;
    }

    public static void encode(ChargeInputPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.pressed);
    }

    public static ChargeInputPacket decode(FriendlyByteBuf buf) {
        return new ChargeInputPacket(buf.readBoolean());
    }

    public static void handle(ChargeInputPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

            System.out.println("[Axiom] Server paketi aldi: " + player.getGameProfile().getName() + " pressed=" + msg.pressed);

            // TODO: burada istersen sunucu tarafi dogrulama/cooldown kontrolu ekle
            // (ornegin oyuncu zaten baska bir sey yapiyorsa charge baslatma).

            long gameTime = player.level().getGameTime();
            ChargeSyncPacket sync = new ChargeSyncPacket(player.getId(), msg.pressed, gameTime);

            // Bu oyuncuyu goren HERKESE (kendisi dahil) yeni durumu yayinla,
            // boylece animasyon 3. sahis goruntude de dogru oynar.
            ModNetwork.CHANNEL.send(
                    PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                    sync
            );
        });
        ctx.setPacketHandled(true);
    }
}
