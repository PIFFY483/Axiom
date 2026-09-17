package com.axiom.network;

import com.axiom.client.SkillLookLockClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> Client (sadece hedef oyuncuya, PacketDistributor.PLAYER): skill
 * kullanim SADECE dash+impact penceresi boyunca (sarj asamasi DAHIL DEGIL -
 * bkz. SkillLookLockManager'in ne zaman acquire ettigi) bakis yonunu
 * (mouse look) kilitler/acar.
 *
 * SkillMovementLockPacket'ten (WASD kilidi, sarj basindan itibaren aktif)
 * BILEREK AYRI - ikisi farkli anlarda baslar.
 *
 * locked=true geldiginde, oyuncunun O ANKI bakis acisi (yRot/xRot) "kilitli
 * acisi" olarak yakalanir - dash+impact boyunca kamera/karakter bu aciya
 * sabitlenir (bkz. SkillLookLockClientState.lock, MouseHandlerMixin).
 */
public class SkillLookLockPacket {
    private final boolean locked;

    public SkillLookLockPacket(boolean locked) {
        this.locked = locked;
    }

    public static void encode(SkillLookLockPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.locked);
    }

    public static SkillLookLockPacket decode(FriendlyByteBuf buf) {
        return new SkillLookLockPacket(buf.readBoolean());
    }

    public static void handle(SkillLookLockPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            if (msg.locked) {
                LocalPlayer player = Minecraft.getInstance().player;
                float yaw = player == null ? 0f : player.getYRot();
                float pitch = player == null ? 0f : player.getXRot();
                SkillLookLockClientState.lock(yaw, pitch);
            } else {
                SkillLookLockClientState.unlock();
            }
        }));
        ctx.setPacketHandled(true);
    }
}
