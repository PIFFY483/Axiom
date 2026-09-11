package com.axiom.network;

import com.axiom.anim.EntityPoseStates;
import com.axiom.skill.Skill;
import com.axiom.skill.SkillRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> Client: bir varligin bir skill icin sarj (charge) durumu degisti. */
public class ChargeSyncPacket {

    public final int entityId;
    public final String skillId;
    public final boolean charging;
    public final long stateStartGameTime;

    public ChargeSyncPacket(int entityId, String skillId, boolean charging, long stateStartGameTime) {
        this.entityId = entityId;
        this.skillId = skillId;
        this.charging = charging;
        this.stateStartGameTime = stateStartGameTime;
    }

    public static void encode(ChargeSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeUtf(msg.skillId);
        buf.writeBoolean(msg.charging);
        buf.writeVarLong(msg.stateStartGameTime);
    }

    public static ChargeSyncPacket decode(FriendlyByteBuf buf) {
        return new ChargeSyncPacket(buf.readVarInt(), buf.readUtf(), buf.readBoolean(), buf.readVarLong());
    }

    public static void handle(ChargeSyncPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            Skill skill = SkillRegistry.get(msg.skillId);
            if (skill == null) {
                // Bilinmeyen skillId (ornegin mod uyumsuzlugu) - sessizce yoksay.
                System.out.println("[Axiom] Bilinmeyen skillId, yoksayiliyor: " + msg.skillId);
                return;
            }

            System.out.println("[Axiom] Client sync aldi: entityId=" + msg.entityId
                    + " skill=" + msg.skillId + " charging=" + msg.charging);

            // poseId olarak dogrudan skillId kullaniliyor - ayni entity'de
            // birden fazla skill ayni anda aktif olabilir (EntityPoseStates
            // her poseId'yi kendi icinde izole tutuyor).
            if (msg.charging) {
                EntityPoseStates.startCharging(msg.entityId, skill.id(), skill.animation(), skill.chargeCap(),
                        skill.postChargePlayDuration(), com.axiom.anim.PoseAnimationPlayer.DEFAULT_RETURN_DURATION);
            } else {
                EntityPoseStates.release(msg.entityId, skill.id());
            }
        }));
        ctx.setPacketHandled(true);
    }
}
