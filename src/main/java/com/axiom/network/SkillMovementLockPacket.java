package com.axiom.network;

import com.axiom.client.SkillMovementLockClientState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> Client (sadece hedef oyuncuya, PacketDistributor.PLAYER): skill
 * kullanim suresi (sarj basindan itibaren, dash+impact tamamen bitene kadar)
 * boyunca WASD/zipla hareketini kilitler/acar.
 *
 * SADECE HAREKETI kapsar - bakis yonu/fare icin AYRI ve DAHA GEC baslayan
 * bir kilit var, bkz. SkillLookLockPacket.
 *
 * bkz. com.axiom.skill.SkillMovementLockManager (sunucu, NE ZAMAN ac/kapa
 * kararini verir) ve com.axiom.client.MovementLockClientHandler (client,
 * GERCEK kilitlemeyi uygular).
 */
public class SkillMovementLockPacket {
    private final boolean locked;

    public SkillMovementLockPacket(boolean locked) {
        this.locked = locked;
    }

    public static void encode(SkillMovementLockPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.locked);
    }

    public static SkillMovementLockPacket decode(FriendlyByteBuf buf) {
        return new SkillMovementLockPacket(buf.readBoolean());
    }

    public static void handle(SkillMovementLockPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            if (msg.locked) {
                SkillMovementLockClientState.lock();
            } else {
                SkillMovementLockClientState.unlock();
            }
        }));
        ctx.setPacketHandled(true);
    }
}
