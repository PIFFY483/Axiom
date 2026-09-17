package com.axiom.network;

import com.axiom.skill.ChargeSessionManager;
import com.axiom.skill.Skill;
import com.axiom.skill.SkillMovementLockManager;
import com.axiom.skill.SkillRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/** Client -> Server: oyuncu bir skill slotuna basti/birakti. */
public class ChargeInputPacket {
    public final String skillId;
    public final boolean pressed;

    public ChargeInputPacket(String skillId, boolean pressed) {
        this.skillId = skillId;
        this.pressed = pressed;
    }

    public static void encode(ChargeInputPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.skillId);
        buf.writeBoolean(msg.pressed);
    }

    public static ChargeInputPacket decode(FriendlyByteBuf buf) {
        return new ChargeInputPacket(buf.readUtf(), buf.readBoolean());
    }

    public static void handle(ChargeInputPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

            System.out.println("[Axiom] Server paketi aldi: " + player.getGameProfile().getName()
                    + " skill=" + msg.skillId + " pressed=" + msg.pressed);

            // TODO: burada istersen sunucu tarafi dogrulama/cooldown kontrolu ekle
            // (ornegin oyuncu zaten baska bir sey yapiyorsa charge baslatma,
            // ya da msg.skillId'nin oyuncunun gercekten sahip oldugu bir skill
            // olup olmadigini kontrol et).

            long gameTime = player.level().getGameTime();

            // Skill'in sunucu-taraf etkisini (varsa) tetiklemek icin sarj
            // sessiyonunu yonet - CHARGE_PHASE_END'e otomatik ulasma
            // ChargeSessionManager.tickAll() uzerinden ayrica isleniyor.
            Skill skill = SkillRegistry.get(msg.skillId);
            if (skill != null) {
                if (msg.pressed) {
                    ChargeSessionManager.startCharging(player, msg.skillId, gameTime);
                    // Hareket (WASD/zipla) kilidi sarj basladigi andan itibaren
                    // devreye giriyor - skill kullanim suresi boyunca (sarj +
                    // dash + impact) karakter klavyeyle hareket ETTIRILEMEZ.
                    // Bakis yonu/fare kilidi BUNDAN AYRI ve SADECE dash
                    // basladiginda devreye giriyor (bkz. SkillLookLockManager
                    // ve SkillTriggerScheduler.tickAll) - sarj sirasinda
                    // oyuncu serbestce bakabilir.
                    if (skill.effect() != null && skill.effect().locksMovementAndLook()) {
                        SkillMovementLockManager.acquire(player);
                    }
                } else {
                    ChargeSessionManager.releaseEarly(player, skill, gameTime);
                }
            }

            ChargeSyncPacket sync = new ChargeSyncPacket(player.getId(), msg.skillId, msg.pressed, gameTime);

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
