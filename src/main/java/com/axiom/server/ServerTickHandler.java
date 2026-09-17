package com.axiom.server;

import com.axiom.Axiom;
import com.axiom.skill.ChargeSessionManager;
import com.axiom.skill.SkillLookLockManager;
import com.axiom.skill.SkillMovementLockManager;
import com.axiom.skill.SkillTriggerScheduler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Tus birakilmadan sarj tavanina (chargeCap) ulasan skill'leri otomatik
 * tetiklemek icin her sunucu tick'inde ChargeSessionManager.tickAll()
 * cagirir. Bkz. ChargeSessionManager sinif yorumu.
 */
@Mod.EventBusSubscriber(modid = Axiom.MOD_ID)
public class ServerTickHandler {

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        ChargeSessionManager.tickAll(server);
        SkillTriggerScheduler.tickAll(server);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ChargeSessionManager.clearPlayer(player);
            // Hareket VE bakis kilidi haritalari da temizlenmeli - aksi
            // halde oyuncu sarj/skill ortasinda cikarsa kilit sonsuza kadar
            // (kullanilmayan bir UUID icin) sunucu haritasinda kalir.
            SkillMovementLockManager.clearPlayer(player);
            SkillLookLockManager.clearPlayer(player);
        }
    }
}
