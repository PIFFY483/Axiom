package com.axiom.network;

import com.axiom.Axiom;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class ModNetwork {
    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Axiom.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int nextId = 0;

    public static void register() {
        CHANNEL.registerMessage(nextId++, ChargeInputPacket.class,
                ChargeInputPacket::encode, ChargeInputPacket::decode, ChargeInputPacket::handle);

        CHANNEL.registerMessage(nextId++, ChargeSyncPacket.class,
                ChargeSyncPacket::encode, ChargeSyncPacket::decode, ChargeSyncPacket::handle);
    }
}
