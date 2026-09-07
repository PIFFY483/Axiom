package com.axiom.client.gui;

import com.axiom.client.AxiomKeys;
import com.axiom.client.skill.SkillSlots;
import com.axiom.skill.Skill;
import com.axiom.skill.SkillRegistry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Basit surukle-birak skill menusu:
 *  - Ust tarafta SkillRegistry'deki TUM skiller kutucuk olarak listelenir.
 *  - Alt tarafta AxiomKeys.SKILL_SLOTS ile birebir eslesen slot kutulari var
 *    (her birinin altinda hangi tusa bagli oldugu yaziyor - varsayilan R/G/H/J).
 *  - Bir skill kutusuna sol tikla, suruklerken kutu imleci takip eder, bir
 *    slotun uzerinde birakinca SkillSlots.assign(...) ile kayit edilir.
 *  - Bir slota sag tiklarsan icindeki skill kaldirilir (SkillSlots.clear).
 *
 * Ikon texture'in yoksa hicbir sorun yok: kutu icine skill isminin ilk
 * harfleri yaziliyor. Skill(...) constructor'ina ResourceLocation icon
 * verdiginde otomatik olarak texture'a gecer (bkz. drawSkillIcon).
 */
public class SkillMenuScreen extends Screen {

    private static final int ICON_SIZE = 32;
    private static final int ICON_GAP = 10;
    private static final int SLOT_SIZE = 40;
    private static final int SLOT_GAP = 16;

    private record IconBox(int x, int y, int size, Skill skill) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + size && my >= y && my < y + size;
        }
    }

    private record SlotBox(int x, int y, int size, int slotIndex) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + size && my >= y && my < y + size;
        }
    }

    private final List<IconBox> iconBoxes = new ArrayList<>();
    private final List<SlotBox> slotBoxes = new ArrayList<>();

    private Skill draggingSkill = null;

    public SkillMenuScreen() {
        super(Component.literal("Axiom - Skiller"));
    }

    @Override
    protected void init() {
        super.init();
        iconBoxes.clear();
        slotBoxes.clear();

        // --- Skill listesi (ust taraf, ortalanmis tek satir) ---
        List<Skill> skills = new ArrayList<>(SkillRegistry.all());
        int totalIconWidth = skills.size() * ICON_SIZE + Math.max(0, skills.size() - 1) * ICON_GAP;
        int iconStartX = (this.width - totalIconWidth) / 2;
        int iconY = this.height / 2 - 60;

        for (int i = 0; i < skills.size(); i++) {
            int x = iconStartX + i * (ICON_SIZE + ICON_GAP);
            iconBoxes.add(new IconBox(x, iconY, ICON_SIZE, skills.get(i)));
        }

        // --- Slotlar (alt taraf, AxiomKeys.SKILL_SLOTS ile birebir) ---
        int slotCount = AxiomKeys.SKILL_SLOTS.length;
        int totalSlotWidth = slotCount * SLOT_SIZE + Math.max(0, slotCount - 1) * SLOT_GAP;
        int slotStartX = (this.width - totalSlotWidth) / 2;
        int slotY = this.height / 2 + 20;

        for (int i = 0; i < slotCount; i++) {
            int x = slotStartX + i * (SLOT_SIZE + SLOT_GAP);
            slotBoxes.add(new SlotBox(x, slotY, SLOT_SIZE, i));
        }
    }

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gg);

        gg.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 90, 0xFFFFFF);
        gg.drawCenteredString(this.font, "Bir skilli sec, sonra asagidaki bir slota birak", this.width / 2, this.height / 2 - 78, 0xAAAAAA);

        // Skill kutulari
        for (IconBox box : iconBoxes) {
            boolean hovered = box.contains(mouseX, mouseY);
            boolean beingDragged = draggingSkill == box.skill();
            drawSkillIcon(gg, box.x(), box.y(), box.size(), box.skill(), hovered && !beingDragged);
        }

        // Slot kutulari
        for (SlotBox box : slotBoxes) {
            String assignedId = SkillSlots.get(box.slotIndex());
            Skill assigned = assignedId == null ? null : SkillRegistry.get(assignedId);
            boolean hovered = box.contains(mouseX, mouseY);

            gg.fill(box.x() - 1, box.y() - 1, box.x() + box.size() + 1, box.y() + box.size() + 1,
                    hovered ? 0xFFFFFFFF : 0xFF555555);
            gg.fill(box.x(), box.y(), box.x() + box.size(), box.y() + box.size(), 0xFF1E1E1E);

            if (assigned != null) {
                drawSkillIcon(gg, box.x() + 4, box.y() + 4, box.size() - 8, assigned, false);
            }

            String keyName = AxiomKeys.SKILL_SLOTS[box.slotIndex()].getTranslatedKeyMessage().getString();
            gg.drawCenteredString(this.font, keyName, box.x() + box.size() / 2, box.y() + box.size() + 4, 0xFFFFFF);
        }

        // Suruklenen ikon, imlecin uzerinde
        if (draggingSkill != null) {
            drawSkillIcon(gg, mouseX - ICON_SIZE / 2, mouseY - ICON_SIZE / 2, ICON_SIZE, draggingSkill, false);
        }

        super.render(gg, mouseX, mouseY, partialTick);
    }

    private void drawSkillIcon(GuiGraphics gg, int x, int y, int size, Skill skill, boolean hovered) {
        gg.fill(x - 1, y - 1, x + size + 1, y + size + 1, hovered ? 0xFFFFFFFF : 0xFF888888);
        if (skill.icon() != null) {
            gg.blit(skill.icon(), x, y, 0, 0, size, size, size, size);
        } else {
            // Texture yok - yer tutucu olarak yumusak bir renk + isim baslangici.
            gg.fill(x, y, x + size, y + size, 0xFF3A3A5A);
            String initial = skill.displayName().isEmpty() ? "?" : skill.displayName().substring(0, 1).toUpperCase();
            gg.drawCenteredString(this.font, initial, x + size / 2, y + size / 2 - 4, 0xFFFFFF);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (IconBox box : iconBoxes) {
                if (box.contains(mouseX, mouseY)) {
                    draggingSkill = box.skill();
                    return true;
                }
            }
        } else if (button == 1) {
            for (SlotBox box : slotBoxes) {
                if (box.contains(mouseX, mouseY)) {
                    SkillSlots.clear(box.slotIndex());
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingSkill != null) {
            for (SlotBox box : slotBoxes) {
                if (box.contains(mouseX, mouseY)) {
                    SkillSlots.assign(box.slotIndex(), draggingSkill.id());
                    break;
                }
            }
            draggingSkill = null;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
