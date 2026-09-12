package com.axiom.network;

import com.axiom.client.fx.ShockwaveFxRenderer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> Client (TRACKING_ENTITY_AND_SELF): Dash Uppercut'in yumrugu
 * hedefe DEGDIGI anda (bkz. DashUppercutEffect.tryImpact) sag kolun
 * UCUNDAN cikan, kucukten buyuyerek genisleyen sok dalgasi dizisini
 * tetikler.
 *
 * DashShockwaveFxPacket'ten (dash'in ARKASINDAN, sadece yatay yonde cikan
 * enerji halkasi) farkli olarak bu efekt TAM 3B bir yon (dirX/dirY/dirZ)
 * tasir - kolun o anki (yere gore) egimine gore, yukari/asagi/one her acida
 * olabilir. Bu da HERKESE gonderiliyor - vurusu yapan kisiyi izleyen
 * herkesin ekraninda gorunmesi gerekiyor (bkz. ShockwaveFxRenderer).
 */
public class PunchShockwaveFxPacket {
    private final double x, y, z;
    private final float dirX, dirY, dirZ;
    private final float power;

    public PunchShockwaveFxPacket(double x, double y, double z, float dirX, float dirY, float dirZ, float power) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.dirX = dirX;
        this.dirY = dirY;
        this.dirZ = dirZ;
        this.power = power;
    }

    public static void encode(PunchShockwaveFxPacket msg, FriendlyByteBuf buf) {
        buf.writeDouble(msg.x);
        buf.writeDouble(msg.y);
        buf.writeDouble(msg.z);
        buf.writeFloat(msg.dirX);
        buf.writeFloat(msg.dirY);
        buf.writeFloat(msg.dirZ);
        buf.writeFloat(msg.power);
    }

    public static PunchShockwaveFxPacket decode(FriendlyByteBuf buf) {
        double x = buf.readDouble();
        double y = buf.readDouble();
        double z = buf.readDouble();
        float dirX = buf.readFloat();
        float dirY = buf.readFloat();
        float dirZ = buf.readFloat();
        float power = buf.readFloat();
        return new PunchShockwaveFxPacket(x, y, z, dirX, dirY, dirZ, power);
    }

    public static void handle(PunchShockwaveFxPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ShockwaveFxRenderer.spawnPunchBurst(
                        new Vec3(msg.x, msg.y, msg.z), new Vec3(msg.dirX, msg.dirY, msg.dirZ), msg.power)));
        ctx.setPacketHandled(true);
    }
}
