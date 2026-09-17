package com.axiom.client.fx;

import com.axiom.Axiom;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * ShockwaveFxRenderer'daki AYNI teknikle (vanilla particle KULLANMADAN,
 * RenderLevelStageEvent icinde ozel Tesselator vertex buffer'i ile) iki ayri
 * "ruzgar" efekti cizer:
 *
 *  1) Ucus izi (spawnFlightTrail) - Dash Uppercut'in atilma/ucus fazi
 *     boyunca (DashUppercutEffect.onDash -> travelSeconds kadar) karakterin
 *     etrafinda beliren, geriye dogru uzayip solan ince yari-saydam
 *     cizgiler ("wind streaks"). Karakter hareket halinde oldugu icin sabit
 *     bir nokta degil, entity ID uzerinden her frame guncel pozisyonu takip
 *     eder.
 *
 *  2) Ruzgar tuyu (spawnVacuumEmitter) - yumruk aninda, SONUCTAN BAGIMSIZ
 *     (isabet etsin, iskalasin ya da hic hedef olmasin - VacuumWindFxPacket
 *     HER UCUNDE de gonderilir, bkz. DashUppercutEffect.sendVacuumWind).
 *     TEK SEFERLIK bir patlama DEGIL - 2.5 saniye boyunca karakterin O ANKI
 *     pozisyon+bakisini periyodik olarak yeniden ornekleyip (bkz.
 *     processVacuumEmitters) surekli yeni tuy ciftleri doguran bir yayici.
 *     Ucus izindeki AYNI kameraya-donuk serit teknigini kullanir
 *     (spiral/huni DEGIL - onceki tasarim begenilmedigi icin degistirildi).
 */
@Mod.EventBusSubscriber(modid = Axiom.MOD_ID, value = Dist.CLIENT)
public final class WindTrailFxRenderer {
    private WindTrailFxRenderer() {}

    // ── Ucus izi ayarlari ──
    private static final int STREAK_COUNT = 5;
    private static final float STREAK_MIN_RADIUS = 0.25f;
    private static final float STREAK_MAX_RADIUS = 0.55f;
    private static final float STREAK_LENGTH_MIN = 1.2f;   // az sarj
    private static final float STREAK_LENGTH_MAX = 2.6f;   // tam sarj
    private static final float STREAK_WIDTH = 0.05f;
    private static final float STREAK_CYCLE_SECONDS = 0.35f; // bir cizginin dogup-kaybolma dongusu
    private static final float FLIGHT_COLOR_R = 0.86f;
    private static final float FLIGHT_COLOR_G = 0.93f;
    private static final float FLIGHT_COLOR_B = 1.00f;

    // ── Yukari yukselen ruzgar tuyu ayarlari (spawnVacuumWisps) ──
    // Onceki spiral/huni tasarimi begenilmedi - artik ucus izindeki AYNI
    // gorsel dili (ince, kameraya-donuk, yari-saydam serit) kullaniyor,
    // sadece dikey yukselip hafifce salinan bir "tuy" gibi.
    private static final int VACUUM_WISP_COUNT = 2;
    private static final long VACUUM_DURATION_NANOS = 550_000_000L; // 0.55s
    private static final float VACUUM_MAX_HEIGHT = 1.5f;
    private static final float VACUUM_BASE_RADIUS_MIN = 0.08f;
    private static final float VACUUM_BASE_RADIUS_MAX = 0.28f;
    private static final float VACUUM_SWAY_DEGREES = 16f;      // hafif sag-sol salinma genligi
    private static final float VACUUM_TRAIL_FRACTION = 0.22f;  // tuyun kendi "kuyruk" uzunlugu (omur oraninda)
    private static final float VACUUM_WIDTH = 0.055f;
    private static final float VACUUM_COLOR_R = 0.90f;
    private static final float VACUUM_COLOR_G = 0.96f;
    private static final float VACUUM_COLOR_B = 1.00f;

    // YENI: tek seferlik "patlama" yerine, yumruk aninda dogan bir yayici
    // (VacuumEmitterFx) EMITTER_DURATION_NANOS boyunca PULSE_INTERVAL_NANOS'ta
    // bir yeni tuy cifti dogurur - her seferinde karakterin O ANKI pozisyonundan
    // (bkz. processVacuumEmitters). Boylece hem "efekt karakterin arkasinda
    // yanlis yerden cikiyor" sorunu (ilk pulse animasyon gecisi sirasinda
    // yakalanmis olsa bile sonrakiler duzelir) hem de "sureklilik" istegi
    // ayni cozumle karsilaniyor.
    private static final long EMITTER_DURATION_NANOS = 2_500_000_000L; // 2.5s
    private static final long PULSE_INTERVAL_NANOS = 350_000_000L;     // 0.35s'de bir yeni tuy cifti
    private static final double VACUUM_SHOULDER_HEIGHT_FRACTION = 0.75;
    private static final double VACUUM_FORWARD_REACH = 0.9;

    private static final Random RANDOM = new Random();

    private static final List<FlightWindFx> FLIGHT_ACTIVE = new CopyOnWriteArrayList<>();
    private static final List<VacuumWispFx> VACUUM_ACTIVE = new CopyOnWriteArrayList<>();
    private static final List<VacuumEmitterFx> VACUUM_EMITTERS = new CopyOnWriteArrayList<>();

    /** DashWindTrailFxPacket.handle() tarafindan cagrilir. */
    public static void spawnFlightTrail(int entityId, float dirX, float dirZ, float power, float durationSeconds) {
        float[] angle = new float[STREAK_COUNT];
        float[] radius = new float[STREAK_COUNT];
        float[] phase = new float[STREAK_COUNT];
        for (int i = 0; i < STREAK_COUNT; i++) {
            angle[i] = (float) (Math.PI * 2.0 * i / STREAK_COUNT) + RANDOM.nextFloat() * 0.6f;
            radius[i] = STREAK_MIN_RADIUS + RANDOM.nextFloat() * (STREAK_MAX_RADIUS - STREAK_MIN_RADIUS);
            phase[i] = RANDOM.nextFloat(); // hepsi ayni anda dogmasin
        }
        long durationNanos = (long) (Math.max(0.05f, durationSeconds) * 1_000_000_000L);
        FLIGHT_ACTIVE.add(new FlightWindFx(entityId, dirX, dirZ, clamp01(power),
                System.nanoTime(), durationNanos, angle, radius, phase));
    }

    /**
     * VacuumWindFxPacket.handle() tarafindan cagrilir - yumruk aninda
     * (isabet/iskalama/hedefsiz farketmez) tetiklenir. Tek seferlik bir
     * patlama DEGIL - EMITTER_DURATION_NANOS (2.5s) boyunca karakterin o anki
     * pozisyonunu takip ederek PERIYODIK olarak yeni tuy cifti dogurur (bkz.
     * processVacuumEmitters).
     */
    public static void spawnVacuumEmitter(int entityId, float power) {
        long now = System.nanoTime();
        // lastPulseNanos'u gecmise atiyoruz ki ilk pulse hemen bu frame'de tetiklensin.
        VACUUM_EMITTERS.add(new VacuumEmitterFx(entityId, clamp01(power), now, now - PULSE_INTERVAL_NANOS));
    }

    /** Bir pulse aninda tek bir tuy ciftini dogurur - processVacuumEmitters tarafindan cagrilir. */
    private static void spawnVacuumWispPair(Vec3 origin, float power) {
        long now = System.nanoTime();
        for (int i = 0; i < VACUUM_WISP_COUNT; i++) {
            float baseAngle = RANDOM.nextFloat() * 360f;
            float baseRadius = VACUUM_BASE_RADIUS_MIN
                    + RANDOM.nextFloat() * (VACUUM_BASE_RADIUS_MAX - VACUUM_BASE_RADIUS_MIN);
            float swayDir = (i % 2 == 0) ? 1f : -1f;
            VACUUM_ACTIVE.add(new VacuumWispFx(origin, baseAngle, baseRadius, swayDir, now, VACUUM_DURATION_NANOS));
        }
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

        long now = System.nanoTime();
        // Yayicilari HER ZAMAN isle (VACUUM_ACTIVE/FLIGHT_ACTIVE o an bos olsa
        // bile) - aksi halde iki pulse arasindaki bosluklarda sonraki pulse hic
        // tetiklenmeyebilir.
        processVacuumEmitters(now);

        if (FLIGHT_ACTIVE.isEmpty() && VACUUM_ACTIVE.isEmpty()) return;

        PoseStack poseStack = event.getPoseStack();
        Vec3 camPos = event.getCamera().getPosition();

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tesselator = Tesselator.getInstance();

        renderFlightTrails(poseStack, tesselator, now, camPos, event.getPartialTick());
        renderVacuumWisps(poseStack, tesselator, now, camPos);

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        poseStack.popPose();
    }

    /**
     * Her aktif VacuumEmitterFx icin: suresi (2.5s) dolduysa kaldirir; PULSE_INTERVAL_NANOS
     * gectiyse, entity'nin O ANKI (guncel) pozisyon+bakisindan yeni bir origin
     * hesaplayip yeni bir tuy cifti dogurur. Karakter animasyon gecisini
     * (RETURNING) tamamlayip yerine oturdukten sonraki pulse'lar boylece hep
     * DOGRU noktadan cikar.
     */
    private static void processVacuumEmitters(long now) {
        if (VACUUM_EMITTERS.isEmpty()) return;
        if (Minecraft.getInstance().level == null) {
            VACUUM_EMITTERS.clear();
            return;
        }

        Iterator<VacuumEmitterFx> it = VACUUM_EMITTERS.iterator();
        while (it.hasNext()) {
            VacuumEmitterFx emitter = it.next();
            if (now - emitter.spawnNanos >= EMITTER_DURATION_NANOS) {
                VACUUM_EMITTERS.remove(emitter);
                continue;
            }
            if (now - emitter.lastPulseNanos < PULSE_INTERVAL_NANOS) continue;

            Entity entity = Minecraft.getInstance().level.getEntity(emitter.entityId);
            if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
                VACUUM_EMITTERS.remove(emitter);
                continue;
            }

            Vec3 forward = flatLook(living);
            double shoulderHeight = living.getBbHeight() * VACUUM_SHOULDER_HEIGHT_FRACTION;
            Vec3 origin = living.position().add(0, shoulderHeight, 0).add(forward.scale(VACUUM_FORWARD_REACH));
            spawnVacuumWispPair(origin, emitter.power);
            emitter.lastPulseNanos = now;
        }
    }

    private static Vec3 flatLook(LivingEntity living) {
        Vec3 look = living.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        return flat.lengthSqr() > 1.0e-6 ? flat.normalize() : new Vec3(0, 0, 1);
    }

    private static void renderFlightTrails(PoseStack poseStack, Tesselator tesselator, long now, Vec3 camPos,
                                            float partialTick) {
        Iterator<FlightWindFx> it = FLIGHT_ACTIVE.iterator();
        while (it.hasNext()) {
            FlightWindFx fx = it.next();
            long age = now - fx.spawnNanos;
            if (age >= fx.durationNanos) {
                FLIGHT_ACTIVE.remove(fx);
                continue;
            }

            if (Minecraft.getInstance().level == null) continue;
            Entity entity = Minecraft.getInstance().level.getEntity(fx.entityId);
            if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
                FLIGHT_ACTIVE.remove(fx);
                continue;
            }

            Vec3 center = living.getPosition(partialTick).add(0, living.getBbHeight() * 0.5, 0);
            Vec3 dir = new Vec3(fx.dirX, 0, fx.dirZ);
            if (dir.lengthSqr() < 1.0e-6) dir = new Vec3(0, 0, 1);
            dir = dir.normalize();
            Vec3 right = new Vec3(-dir.z, 0, dir.x);

            float length = STREAK_LENGTH_MIN + (STREAK_LENGTH_MAX - STREAK_LENGTH_MIN) * fx.power;
            float ageSeconds = age / 1_000_000_000f;

            for (int i = 0; i < fx.angle.length; i++) {
                // Her cizgi kendi fazindan baslayarak STREAK_CYCLE_SECONDS'ta bir
                // dogup-kayboluyor - surekli akan bir "ruzgar" hissi icin.
                float cycleT = ((ageSeconds / STREAK_CYCLE_SECONDS) + fx.phase[i]) % 1.0f;

                double cos = Math.cos(fx.angle[i]);
                double sin = Math.sin(fx.angle[i]);
                Vec3 lateral = right.scale(cos).add(new Vec3(0, sin, 0)).scale(fx.radius[i]);
                // Cizgi, karakterin biraz ONUNDEN baslayip GERIYE (dir'in tersine)
                // dogru uzanir - kameraya/izleyiciye dogru akan ruzgar hissi.
                Vec3 headOffset = dir.scale(0.4 - cycleT * (0.4 + length));
                Vec3 head = center.add(lateral).add(headOffset);
                Vec3 tail = head.subtract(dir.scale(length * (1.0f - cycleT * 0.3f)));

                float alpha = (float) (Math.sin(Math.PI * Math.min(1.0, cycleT * 3.0)) * 0.5) * (1.0f - cycleT * 0.4f);
                alpha = Math.max(0f, Math.min(0.5f, alpha));
                if (alpha <= 0.01f) continue;

                drawStreak(poseStack, tesselator, head, tail, camPos,
                        FLIGHT_COLOR_R, FLIGHT_COLOR_G, FLIGHT_COLOR_B, alpha, STREAK_WIDTH);
            }
        }
    }

    private static void renderVacuumWisps(PoseStack poseStack, Tesselator tesselator, long now, Vec3 camPos) {
        Iterator<VacuumWispFx> it = VACUUM_ACTIVE.iterator();
        while (it.hasNext()) {
            VacuumWispFx fx = it.next();
            long age = now - fx.spawnNanos;
            if (age >= fx.durationNanos) {
                VACUUM_ACTIVE.remove(fx);
                continue;
            }

            float t = age / (float) fx.durationNanos; // 0..1
            float headT = t;
            float tailT = Math.max(0f, t - VACUUM_TRAIL_FRACTION);

            Vec3 head = vacuumWispPoint(fx, headT);
            Vec3 tail = vacuumWispPoint(fx, tailT);

            // Basta hafifce belirip, sonda hizla solerek kaybolan bir alfa -
            // "havanin yukari cekilip kaybolmasi" hissi.
            float fadeIn = Math.min(1f, t / 0.15f);
            float fadeOut = 1.0f - t * t;
            float alpha = 0.55f * fadeIn * fadeOut;
            if (alpha <= 0.01f) continue;

            drawStreak(poseStack, tesselator, head, tail, camPos,
                    VACUUM_COLOR_R, VACUUM_COLOR_G, VACUUM_COLOR_B, alpha, VACUUM_WIDTH);
        }
    }

    /**
     * t=0 (taban, origin civari) .. t=1 (VACUUM_MAX_HEIGHT kadar yukselmis)
     * arasi bir ruzgar tuyu noktasi - tam sarmal DEGIL, sadece rüzgarda
     * salinan bir tuy gibi hafifce sag-sola sallaniyor.
     */
    private static Vec3 vacuumWispPoint(VacuumWispFx fx, float t) {
        float rise = 1.0f - (1.0f - t) * (1.0f - t); // ease-out yukselis
        float height = VACUUM_MAX_HEIGHT * rise;
        float sway = (float) Math.sin(t * Math.PI * 1.6) * VACUUM_SWAY_DEGREES * fx.swayDir;
        double angleRad = Math.toRadians(fx.baseAngleDeg + sway);
        float radius = fx.baseRadius * (1.0f - t * 0.4f); // yukselirken hafifce ice dogru daralir
        return fx.origin.add(Math.cos(angleRad) * radius, height, Math.sin(angleRad) * radius);
    }

    private static void drawStreak(PoseStack poseStack, Tesselator tesselator, Vec3 head, Vec3 tail, Vec3 camPos,
                                    float r, float g, float b, float alpha, float width) {
        // Cizgiyi izleyiciye donuk (camera-facing) ince bir serit olarak ciz:
        // genislik ekseni, cizginin kendi yonu ile "cizgiden kameraya" yonunun
        // cross-carpimindan turetilir (klasik billboard-hat teknigi).
        Vec3 lineDir = head.subtract(tail);
        if (lineDir.lengthSqr() < 1.0e-6) return;
        lineDir = lineDir.normalize();
        Vec3 toCam = head.add(tail).scale(0.5).subtract(camPos);
        Vec3 widthAxis = lineDir.cross(toCam);
        if (widthAxis.lengthSqr() < 1.0e-6) widthAxis = new Vec3(0, 1, 0);
        widthAxis = widthAxis.normalize().scale(width);

        Matrix4f matrix = poseStack.last().pose();
        BufferBuilder buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);

        Vec3 h1 = head.add(widthAxis);
        Vec3 h2 = head.subtract(widthAxis);
        Vec3 t1 = tail.add(widthAxis);
        Vec3 t2 = tail.subtract(widthAxis);

        // Bas (head) tarafi tam alpha, kuyruk (tail) tarafi sifira dogru sonuyor
        // - "ruzgarin kaydigi" hissi icin.
        buffer.vertex(matrix, (float) h1.x, (float) h1.y, (float) h1.z).color(r, g, b, alpha).endVertex();
        buffer.vertex(matrix, (float) h2.x, (float) h2.y, (float) h2.z).color(r, g, b, alpha).endVertex();
        buffer.vertex(matrix, (float) t1.x, (float) t1.y, (float) t1.z).color(r, g, b, 0f).endVertex();
        buffer.vertex(matrix, (float) t2.x, (float) t2.y, (float) t2.z).color(r, g, b, 0f).endVertex();

        tesselator.end();
    }

    private static float clamp01(float v) { return v < 0f ? 0f : Math.min(1f, v); }
}
