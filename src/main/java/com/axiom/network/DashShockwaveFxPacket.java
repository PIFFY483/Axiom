package com.axiom.network;

import com.axiom.client.fx.ShockwaveFxRenderer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> Client (TRACKING_ENTITY_AND_SELF): dash baslarken karakterin
 * ARKASINDAN cikan enerji halkasi efektini tetikler.
 *
 * DashUppercutFxPacket'ten (sadece dashi yapan oyuncuya giden kamera fx)
 * farkli olarak bu HERKESE gonderiliyor - cunku bu gercek bir world-space
 * render (bkz. ShockwaveFxRenderer), sadece dashi yapan kisinin ekraninda
 * degil, onu goren herkesin ekraninda cizilmesi gerekiyor.
 */
public class DashShockwaveFxPacket {
    private final double x, y, z;
    private final float dirX, dirZ;
    private final float power;
    private final int count;

    public DashShockwaveFxPacket(double x, double y, double z, float dirX, float dirZ, float power, int count) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.dirX = dirX;
        this.dirZ = dirZ;
        this.power = power;
        this.count = count;
    }

    public static void encode(DashShockwaveFxPacket msg, FriendlyByteBuf buf) {
        buf.writeDouble(msg.x);
        buf.writeDouble(msg.y);
        buf.writeDouble(msg.z);
        buf.writeFloat(msg.dirX);
        buf.writeFloat(msg.dirZ);
        buf.writeFloat(msg.power);
        buf.writeByte(msg.count);
    }

    public static DashShockwaveFxPacket decode(FriendlyByteBuf buf) {
        double x = buf.readDouble();
        double y = buf.readDouble();
        double z = buf.readDouble();
        float dirX = buf.readFloat();
        float dirZ = buf.readFloat();
        float power = buf.readFloat();
        int count = buf.readByte();
        return new DashShockwaveFxPacket(x, y, z, dirX, dirZ, power, count);
    }

    public static void handle(DashShockwaveFxPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ShockwaveFxRenderer.spawnSequence(
                        new Vec3(msg.x, msg.y, msg.z), msg.dirX, msg.dirZ, msg.power, msg.count)));
        ctx.setPacketHandled(true);
    }
}
