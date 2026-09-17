package com.axiom.client;

import com.axiom.Axiom;
import net.minecraft.client.player.Input;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * SkillMovementLockClientState.isLocked() true iken WASD/zipla girdisini
 * sifirlar - yani karakter skill kullanim suresi boyunca klavyeyle hareket
 * ETTIRILEMEZ.
 *
 * Bakis yonu (mouse look) kilidi BURADA DEGIL - bu event sadece klavye
 * hareket girdisini kapsiyor. Bakis kilidi icin bkz. ClientTickHandler
 * (her tick sonunda yRot/xRot'u kilitli aciya dondurur).
 */
@Mod.EventBusSubscriber(modid = Axiom.MOD_ID, value = Dist.CLIENT)
public final class MovementLockClientHandler {
    private MovementLockClientHandler() {}

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!SkillMovementLockClientState.isLocked()) return;

        Input input = event.getInput();
        input.up = false;
        input.down = false;
        input.left = false;
        input.right = false;
        input.jumping = false;
        input.forwardImpulse = 0f;
        input.leftImpulse = 0f;
    }
}
