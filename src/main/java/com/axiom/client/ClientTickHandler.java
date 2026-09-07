package com.axiom.client;

import com.axiom.Axiom;
import com.axiom.network.ChargeInputPacket;
import com.axiom.network.ModNetwork;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Axiom.MOD_ID, value = Dist.CLIENT)
public class ClientTickHandler {

    private static boolean wasDown = false;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        boolean isDown = AxiomKeys.ATTACK_CHARGE.isDown();
        if (isDown != wasDown) {
            System.out.println("[Axiom] V " + (isDown ? "basildi" : "birakildi") + " -> paket gonderiliyor");
            ModNetwork.CHANNEL.sendToServer(new ChargeInputPacket(isDown));
            wasDown = isDown;
        }
        // NOT: animasyon zamani artik AxiomAnimationPlayer icinde gercek saatle
        // (System.nanoTime) hesaplaniyor, burada ayrica ilerletmeye gerek yok.
    }
}
