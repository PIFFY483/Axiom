package com.axiom.network;

import com.axiom.client.ClientTargetHighlight;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> Client: bir Dash Uppercut hedefi kilitlendi (locked=true) ya da
 * is bitti - hit/miss/iptal fark etmeden (locked=false).
 *
 * Glow'u ClientTargetHighlight+MinecraftGlowMixin tetikliyor (entity.setGlowingTag()
 * client'ta calismiyordu - bkz. ClientTargetHighlight javadoc). Renk icin
 * hala client-only bir scoreboard takimi kullaniliyor - bu kisim zaten
 * dogru calisiyordu, Entity.getTeamColor() sadece client'in KENDI
 * Scoreboard'una bakiyor, sunucuyla senkron gerekmiyor.
 */
public class TargetLockPacket {

    private static final String TEAM_NAME = "axiom_target_highlight";

    public final int entityId;
    public final boolean locked;

    public TargetLockPacket(int entityId, boolean locked) {
        this.entityId = entityId;
        this.locked = locked;
    }

    public static void encode(TargetLockPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeBoolean(msg.locked);
    }

    public static TargetLockPacket decode(FriendlyByteBuf buf) {
        return new TargetLockPacket(buf.readVarInt(), buf.readBoolean());
    }

    public static void handle(TargetLockPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> apply(msg)));
        ctx.setPacketHandled(true);
    }

    private static void apply(TargetLockPacket msg) {
        ClientTargetHighlight.set(msg.entityId, msg.locked);

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Entity entity = mc.level.getEntity(msg.entityId);
        if (entity == null) return;

        Scoreboard scoreboard = mc.level.getScoreboard();
        PlayerTeam team = scoreboard.getPlayerTeam(TEAM_NAME);
        if (team == null) {
            team = scoreboard.addPlayerTeam(TEAM_NAME);
            team.setColor(ChatFormatting.RED);
        }

        if (msg.locked) {
            scoreboard.addPlayerToTeam(entity.getStringUUID(), team);
        } else {
            scoreboard.removePlayerFromTeam(entity.getStringUUID(), team);
        }
    }
}
