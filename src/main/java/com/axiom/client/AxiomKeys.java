package com.axiom.client;

import com.axiom.Axiom;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Eski tek ATTACK_CHARGE(V) tusunun yerini SKILL_SLOTS aldi: artik sabit bir
 * poza degil, "slot"lara basiyoruz. Hangi slotta hangi skill oldugu
 * SkillMenuScreen uzerinden surukle-birak ile SkillSlots'a kaydediliyor.
 *
 * Varsayilan tuslar: R, G, H, J (vanilla ile catismasin diye secildi).
 * Yeni bir slot eklemek istersen bu diziye bir KeyMapping daha eklemen
 * yeterli - SkillSlots.SLOT_COUNT ve SkillMenuScreen otomatik uyum saglar.
 */
public class AxiomKeys {

    public static final KeyMapping[] SKILL_SLOTS = new KeyMapping[]{
            new KeyMapping("key.axiom.skill_slot_1", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, InputConstants.KEY_R, "key.categories.axiom"),
            new KeyMapping("key.axiom.skill_slot_2", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, InputConstants.KEY_G, "key.categories.axiom"),
            new KeyMapping("key.axiom.skill_slot_3", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, InputConstants.KEY_H, "key.categories.axiom"),
            new KeyMapping("key.axiom.skill_slot_4", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, InputConstants.KEY_J, "key.categories.axiom"),
    };

    /** Skil menusunu ac/kapa. */
    public static final KeyMapping OPEN_SKILL_MENU = new KeyMapping(
            "key.axiom.open_skill_menu",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_K,
            "key.categories.axiom"
    );

    @Mod.EventBusSubscriber(modid = Axiom.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class Registration {
        @SubscribeEvent
        public static void register(RegisterKeyMappingsEvent event) {
            for (KeyMapping slot : SKILL_SLOTS) {
                event.register(slot);
            }
            event.register(OPEN_SKILL_MENU);
        }
    }
}
