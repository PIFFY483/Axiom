package com.axiom;

import com.axiom.network.ModNetwork;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(Axiom.MOD_ID)
public class Axiom {

    public static final String MOD_ID = "axiom";

    public Axiom() {
        var modBus = FMLJavaModLoadingContext.get().getModEventBus();
        var forgeBus = MinecraftForge.EVENT_BUS;

        ModNetwork.register();

        // ESKI: modBus.addListener(this::registerReloadListeners) + AxiomAnimationRegistry
        // kaldirildi - artik JSON okuma / resource-pack reload listener'a gerek yok,
        // pozlar EntityPoseStates + hardcoded PoseAnimation siniflariyla calisiyor.
    }
}
