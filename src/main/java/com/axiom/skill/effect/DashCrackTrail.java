package com.axiom.skill.effect;

import com.axiom.Axiom;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dash sirasinda gecilen yolun uzerine, oyuncunun o anki ayak altindaki
 * blogun kirinti particle'larini (vanilla ParticleTypes.BLOCK) dokerek
 * "yerde catlak/toz izi" hissi verir.
 *
 * Ozel texture/particle tanimi GEREKMEZ - vanilla'nin madencilik/inis/
 * sprint particle sisteminde de kullandigi, o anki BlockState'in
 * texture'indan otomatik kirinti ureten mekanizmanin aynisi. Bu yuzden
 * her zemin turunde (tas, kum, cim...) dogru gorunur.
 *
 * DashUppercutEffect.onDash() -> DashCrackTrail.start(player) ile baslar,
 * sonraki TRAIL_TICKS server tick'i boyunca oyuncunun o anki pozisyonuna
 * particle basilir (yani dash sirasinda GERCEKTEN gecilen yol takip edilir,
 * sabit bir cizgi degil).
 */
@Mod.EventBusSubscriber(modid = Axiom.MOD_ID)
public class DashCrackTrail {

    /** Dash'in "atilma" penceresi kadar iz birak. */
    private static final int TRAIL_TICKS = 6;
    private static final int PARTICLES_PER_TICK = 12;
    private static final double SPREAD_XZ = 0.30;

    private static final Map<UUID, Integer> ACTIVE = new ConcurrentHashMap<>();

    private DashCrackTrail() {}

    /** DashUppercutEffect.onDash() tarafindan cagrilir. */
    public static void start(ServerPlayer player) {
        ACTIVE.put(player.getUUID(), TRAIL_TICKS);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) return;

        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        Iterator<Map.Entry<UUID, Integer>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> entry = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());

            if (player == null || player.isRemoved()) {
                it.remove();
                continue;
            }

            spawnCrack(player);

            int ticksLeft = entry.getValue() - 1;
            if (ticksLeft <= 0) {
                it.remove();
            } else {
                entry.setValue(ticksLeft);
            }
        }
    }

    private static void spawnCrack(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return;

        BlockPos below = player.blockPosition().below();
        BlockState ground = level.getBlockState(below);
        if (ground.isAir()) return;

        BlockParticleOption option = new BlockParticleOption(ParticleTypes.BLOCK, ground);
        level.sendParticles(option,
                player.getX(), player.getY() + 0.05, player.getZ(),
                PARTICLES_PER_TICK,
                SPREAD_XZ, 0.02, SPREAD_XZ,
                0.06);
    }
}
