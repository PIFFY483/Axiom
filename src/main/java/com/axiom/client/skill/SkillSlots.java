package com.axiom.client.skill;

import com.axiom.client.AxiomKeys;

import java.util.HashMap;
import java.util.Map;

/**
 * Client-side: SkillMenuScreen'de bir skill ikonunu bir slota (R/G/H/J
 * tuslarindan birine) surukleyip biraktiginda burada kaydedilir.
 * ClientTickHandler her slot tusunun basili/birakili durumunu izlerken
 * "bu slotta hangi skill var" diye buraya sorar.
 *
 * Simdilik SADECE bellekte tutuluyor - oyunu kapatinca sifirlanir. Ileride
 * kalici olmasini istersen assign()/clear() cagrildiginda basit bir
 * .properties veya JSON dosyasina yazip Minecraft.onGameStart'ta okuruz;
 * su an icin bu MVP'nin kapsami disinda tutuldu.
 */
public final class SkillSlots {

    private SkillSlots() {}

    /** Kac tane slot (tus) oldugu - AxiomKeys.SKILL_SLOTS ile birebir eslesir. */
    public static final int SLOT_COUNT = AxiomKeys.SKILL_SLOTS.length;

    private static final Map<Integer, String> ASSIGNED = new HashMap<>();

    public static void assign(int slot, String skillId) {
        if (slot < 0 || slot >= SLOT_COUNT) return;
        if (skillId == null) {
            ASSIGNED.remove(slot);
        } else {
            ASSIGNED.put(slot, skillId);
        }
    }

    /** O slotta atanmis skill id'si, yoksa null. */
    public static String get(int slot) {
        return ASSIGNED.get(slot);
    }

    public static void clear(int slot) {
        ASSIGNED.remove(slot);
    }

    public static boolean isEmpty(int slot) {
        return !ASSIGNED.containsKey(slot);
    }
}
