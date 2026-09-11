package com.axiom.skill.effect;

import com.axiom.anim.pose.DashUppercutPose;
import com.axiom.network.DashUppercutFxPacket;
import com.axiom.network.ModNetwork;
import com.axiom.skill.SkillEffect;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;

/**
 * Dash Uppercut skill'inin sarj-bitisi etkisi, animasyondaki iki ayri anla
 * eslesecek sekilde iki asamali calisir (bkz. SkillEffect):
 *
 *  1) onDash()   - DashUppercutPose.DASH_TRIGGER_TIME'a (2.2s) denk gelen an:
 *                  karakter bakis yonune dogru atilir (leap).
 *  2) tryImpact() - animasyonun bitisine (LENGTH=3.0s, "yumruk" ani) denk
 *                  gelen an: onceden kilitlenen TEK dusman sarj gucune gore
 *                  hem bakis yonune hem yukari firlatilir. Karakter henuz
 *                  menzile girmediyse (uzak hedef, dash yetismedi) bir sure
 *                  daha beklenir - bkz. impactTimeoutGraceSeconds().
 */
public final class DashUppercutEffect implements SkillEffect {
    public static final DashUppercutEffect INSTANCE = new DashUppercutEffect();
    private DashUppercutEffect() {}

    /** chargeRatio 0 olsa bile minik bir etki kalsin diye taban guc orani. */
    private static final float MIN_POWER_FRACTION = 0.25f;

    private static final double FORWARD_RANGE = 3.5;      // hedef arama mesafesi (blok)
    private static final double SEARCH_WIDTH = 2.0;       // arama kutusunun genisligi/yuksekligi (blok)
    private static final double IMPACT_RANGE = 2.2;       // bu mesafeye girince "menzilde" sayilir

    // Sarjla birlikte artan dash hizi - "hedef uzaksa da yetissin" istegi
    // icin baz ve tavan degerler. (Dikey ziplama bilerek YOK - karakter
    // yerden kesilmeden, duz bir atilma yapiyor.)
    private static final double DASH_SPEED_BASE = 0.55;
    private static final double DASH_SPEED_MAX = 1.35;

    private static final double LAUNCH_HORIZONTAL_BASE = 0.9;
    private static final double LAUNCH_HORIZONTAL_MAX = 2.1;
    private static final double LAUNCH_VERTICAL_BASE = 0.35;
    private static final double LAUNCH_VERTICAL_MAX = 1.0;

    private static final float HIT_DAMAGE = 1.0f;

    // DashUppercutPose zamanlamasindan turetilir: DASH_TRIGGER_TIME (2.2s)
    // post-charge penceresinin (2.0->LENGTH, sikistirilmis POST_CHARGE_PLAY_DURATION
    // saniyeye) hangi oranina denk geliyorsa dash o oranda gecikmeyle tetiklenir.
    private static final float ANIM_LENGTH = DashUppercutPose.INSTANCE.length();
    private static final float DASH_DELAY = (DashUppercutPose.DASH_TRIGGER_TIME - DashUppercutPose.CHARGE_PHASE_END)
            / (ANIM_LENGTH - DashUppercutPose.CHARGE_PHASE_END) * DashUppercutPose.POST_CHARGE_PLAY_DURATION;

    // impact ise animasyonun TAM bitisinden degil, IMPACT_LEAD_TIME kadar ONCESINDEN
    // tetiklenir - yumruk gorsel olarak hedefe deger DEGMEZ, "vurus" animasyon
    // tamamen bitip karakter toparlanma pozuna girdikten SONRA gerceklesirdi ki bu
    // tutarsiz hissettiriyordu. 0.1s (2 tick) erken tetiklemek, firlatmayi tam
    // yumrugun degdigi karede hizalar.
    private static final float IMPACT_LEAD_TIME = 0.1f;
    private static final float IMPACT_DELAY = Math.max(DASH_DELAY,
            DashUppercutPose.POST_CHARGE_PLAY_DURATION - IMPACT_LEAD_TIME);

    @Override
    public LivingEntity lockTarget(ServerPlayer player) {
        Vec3 forward = forwardFlat(player);
        Vec3 origin = player.position();
        Vec3 tip = origin.add(forward.scale(FORWARD_RANGE));
        AABB searchBox = new AABB(origin, tip).inflate(SEARCH_WIDTH / 2.0, 1.0, SEARCH_WIDTH / 2.0);

        List<LivingEntity> candidates = player.level().getEntitiesOfClass(
                LivingEntity.class, searchBox,
                e -> e != player && e.isAlive() && !e.isSpectator());

        LivingEntity closest = null;
        double bestDistSq = Double.MAX_VALUE;
        for (LivingEntity e : candidates) {
            double d = e.distanceToSqr(player);
            if (d < bestDistSq) { bestDistSq = d; closest = e; }
        }
        return closest;
    }

    @Override
    public void onDash(ServerPlayer player, float chargeRatio) {
        float power = powerOf(chargeRatio);
        Vec3 forward = forwardFlat(player);
        double speed = lerp(power, DASH_SPEED_BASE, DASH_SPEED_MAX);
        Vec3 dash = forward.scale(speed);
        Vec3 current = player.getDeltaMovement();
        // Sadece yatay atilma - dikey hiza (zipla ma) dokunmuyoruz, karakter
        // yerdeyse yerde kaliyor.
        player.setDeltaMovement(current.x + dash.x, current.y, current.z + dash.z);
        player.hurtMarked = true; // hareketi hemen istemciye senkronize et

        // Ziplama/atilma anindaki hiz hissi icin FOV punch (client-only fx).
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new DashUppercutFxPacket(DashUppercutFxPacket.Stage.LEAP));

        // Gecilen yolun uzerine yere-catlak/toz izi birak.
        DashCrackTrail.start(player);
    }

    @Override
    public boolean tryImpact(ServerPlayer player, LivingEntity target, float chargeRatio, boolean forceIfTimedOut) {
        if (!target.isAlive()) return true; // hedef gitti, beklemeye deger yok

        boolean inRange = player.distanceToSqr(target) <= IMPACT_RANGE * IMPACT_RANGE;
        if (!inRange && !forceIfTimedOut) return false; // henuz ulasilmadi - son karede tutmaya devam

        float power = powerOf(chargeRatio);

        // Kucuk bir isabet hasari - vurus hissi ve i-frame sifirlama icin.
        target.hurt(player.level().damageSources().playerAttack(player), HIT_DAMAGE);

        // Vanilla'nin kendi knockback'ini gormezden gel, kendi firlatmamizi uygula.
        Vec3 forward = forwardFlat(player);
        double horiz = lerp(power, LAUNCH_HORIZONTAL_BASE, LAUNCH_HORIZONTAL_MAX);
        double vert = lerp(power, LAUNCH_VERTICAL_BASE, LAUNCH_VERTICAL_MAX);
        Vec3 launch = forward.scale(horiz).add(0, vert, 0);
        target.setDeltaMovement(launch);
        target.hurtMarked = true;

        // Vurus ani - screen shake + hitstop (client-only fx, bkz. DashUppercutFxPacket).
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new DashUppercutFxPacket(DashUppercutFxPacket.Stage.IMPACT));
        return true;
    }

    @Override public float dashDelaySeconds() { return DASH_DELAY; }
    @Override public float impactDelaySeconds() { return IMPACT_DELAY; }
    @Override public float impactTimeoutGraceSeconds() { return 0.35f; }

    private static float powerOf(float chargeRatio) {
        return MIN_POWER_FRACTION + (1.0f - MIN_POWER_FRACTION) * clamp01(chargeRatio);
    }

    private static Vec3 forwardFlat(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 f = new Vec3(look.x, 0, look.z);
        return f.lengthSqr() > 1.0e-6 ? f.normalize() : new Vec3(0, 0, 1);
    }

    private static double lerp(float t, double a, double b) { return a + (b - a) * t; }
    private static float clamp01(float v) { return v < 0f ? 0f : Math.min(1f, v); }
}
