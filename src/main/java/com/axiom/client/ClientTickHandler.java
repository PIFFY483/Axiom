package com.axiom.client;

import com.axiom.Axiom;
import com.axiom.client.gui.SkillMenuScreen;
import com.axiom.client.skill.SkillSlots;
import com.axiom.network.ChargeInputPacket;
import com.axiom.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Axiom.MOD_ID, value = Dist.CLIENT)
public class ClientTickHandler {

    // Her slot icin ayri "onceki durum" - eskiden tek bir wasDown yeterliydi,
    // artik N tane bagimsiz tus izleniyor.
    private static final boolean[] wasDown = new boolean[AxiomKeys.SKILL_SLOTS.length];

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        // Menu ac/kapa - consumeClick() her tikta cagrilmali (Forge'un GLFW
        // tus kuyrugunu bosaltmasi icin), sadece sonucunu kosullu kullaniyoruz.
        while (AxiomKeys.OPEN_SKILL_MENU.consumeClick()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen == null) {
                mc.setScreen(new SkillMenuScreen());
            }
        }

        for (int slot = 0; slot < AxiomKeys.SKILL_SLOTS.length; slot++) {
            boolean isDown = AxiomKeys.SKILL_SLOTS[slot].isDown();
            if (isDown == wasDown[slot]) continue;
            wasDown[slot] = isDown;

            String skillId = SkillSlots.get(slot);
            if (skillId == null) continue; // bu slota hicbir skill atanmamis, gormezden gel

            System.out.println("[Axiom] Slot " + slot + " (" + skillId + ") " + (isDown ? "basildi" : "birakildi") + " -> paket gonderiliyor");
            ModNetwork.CHANNEL.sendToServer(new ChargeInputPacket(skillId, isDown));
        }
        // NOT: animasyon zamani artik PoseAnimationPlayer icinde gercek saatle
        // (System.nanoTime) hesaplaniyor, burada ayrica ilerletmeye gerek yok.
    }
}
