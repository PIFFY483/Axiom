package com.axiom.client.fx;

import com.axiom.Axiom;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Dash Uppercut baslarken karakterin ARKASINDAN cikan, mesafeye gore 1-3
 * kere art arda patlayan enerji halkasi efekti.
 *
 * BILEREK vanilla particle sistemi KULLANILMIYOR - halkalar dogrudan
 * RenderLevelStageEvent icinde ozel bir vertex buffer ile ciziliyor: dash
 * yonune DIK bakan, disari dogru genisleyip sonen ince bir "enerji halkasi"
 * (particle texture'lariyla elde edilemeyecek pürüzsüz bir sekil/solma).
 *
 * Akis: DashShockwaveFxPacket.handle() -> spawnSequence(...) burada 1-3
 * ShockwaveFx kaydi olusturur (her biri kendi GECIKMELI spawnNanos'uyla -
 * boylece art arda "pat pat pat" patlarlar), onRenderLevelStage() her
 * frame'de aktif kayitlari cizip suresi dolanlari temizler.
 */
@Mod.EventBusSubscriber(modid = Axiom.MOD_ID, value = Dist.CLIENT)
public final class ShockwaveFxRenderer {
    private ShockwaveFxRenderer() {}

    // ── Gorsel ayarlar - istege gore degistirilebilir ──
    private static final float START_RADIUS = 0.15f;
    private static final float RADIUS_MIN = 1.0f;   // az sarj / kisa mesafede halka boyu
    private static final float RADIUS_MAX = 2.2f;   // tam sarj / uzun mesafede halka boyu
    private static final float RING_THICKNESS = 0.16f;
    private static final int SEGMENTS = 32;
    private static final long DURATION_NANOS = 260_000_000L; // tek halkanin omru: 0.26s
    private static final long STAGGER_NANOS = 80_000_000L;   // ardisik halkalar arasi gecikme: 0.08s
    private static final float BACK_STEP = 0.35f;            // her ek halka bir onceki halkanin biraz gerisinde dogar

    // Enerji halkasi rengi (acik mavi-beyaz).
    private static final float COLOR_R = 0.55f;
    private static final float COLOR_G = 0.80f;
    private static final float COLOR_B = 1.00f;

    // ── YUMRUK VURUS SOK DALGASI ayarlari (spawnPunchBurst) ──
    // Dash'in arkadan cikan enerji halkasindan bilerek farkli: daha kisa
    // omurlu, daha carpici/parlak (beyaza yakin) ve art arda PUNCH_RING_COUNT
    // kez (kucukten buyuyerek) patlayan bir "vurus flasi" hissi veriyor.
    private static final float PUNCH_START_RADIUS = 0.10f;
    private static final float PUNCH_RADIUS_MIN = 1.1f;   // az sarj - kucuk sok dalgasi (hafif buyutuldu)
    private static final float PUNCH_RADIUS_MAX = 3.1f;   // tam sarj - buyuk sok dalgasi (hafif buyutuldu)
    private static final float PUNCH_RING_THICKNESS = 0.20f;
    private static final int PUNCH_RING_COUNT = 3;                 // "bircok dalgasi belirgin" olsun diye art arda 3 halka
    private static final long PUNCH_DURATION_NANOS = 300_000_000L; // tek halka omru: 0.30s
    private static final long PUNCH_STAGGER_NANOS = 55_000_000L;   // ardisik halkalar arasi gecikme: 0.055s
    private static final float PUNCH_COLOR_R = 0.95f;
    private static final float PUNCH_COLOR_G = 0.97f;
    private static final float PUNCH_COLOR_B = 1.00f;

    private static final List<ShockwaveFx> ACTIVE = new CopyOnWriteArrayList<>();

    /**
     * DashShockwaveFxPacket.handle() tarafindan cagrilir.
     *
     * @param origin dash baslangicindaki karakter pozisyonu (govde yuksekliginde)
     * @param dirX   yatay dash yonu X bileseni
     * @param dirZ   yatay dash yonu Z bileseni
     * @param power  0-1 sarj orani - halka buyuklugunu olcekler
     * @param count  1-3 arasi, kac halka art arda patlayacak (mesafeye gore sunucuda hesaplanir)
     */
    public static void spawnSequence(Vec3 origin, float dirX, float dirZ, float power, int count) {
        count = Math.max(1, Math.min(3, count));

        Vec3 dir = new Vec3(dirX, 0, dirZ);
        if (dir.lengthSqr() < 1.0e-6) dir = new Vec3(0, 0, 1);
        dir = dir.normalize();

        // Halka duzlemi dash yonune DIK bakar (right x up ~ dir) - yani
        // halka, karakterin arkasinda "gectigi bir portal" gibi durur.
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 right = new Vec3(-dir.z, 0, dir.x); // dir yatay oldugu icin zaten birim uzunlukta

        float maxRadius = RADIUS_MIN + (RADIUS_MAX - RADIUS_MIN) * clamp01(power);
        long now = System.nanoTime();

        for (int i = 0; i < count; i++) {
            Vec3 center = origin.subtract(dir.scale(BACK_STEP * i));
            long spawnAt = now + STAGGER_NANOS * i;
            ACTIVE.add(new ShockwaveFx(center, right, up, maxRadius, spawnAt, DURATION_NANOS,
                    START_RADIUS, RING_THICKNESS, COLOR_R, COLOR_G, COLOR_B));
        }
    }

    /**
     * PunchShockwaveFxPacket.handle() tarafindan cagrilir - Dash Uppercut'in
     * yumrugu hedefe DEGDIGI anda (bkz. DashUppercutEffect.tryImpact) sag
     * kolun UCUNDAN cikan sok dalgasi dizisini baslatir.
     *
     * Dash'in arkadan cikan enerji halkasindan FARKLI olarak halka duzlemi
     * yatay degil - kolun O ANKI vurus yonune (dir) DIK durur. dir, dunya
     * uzayinda kolun/vurusun gercek 3B yonudur (DashUppercutEffect zaten
     * kolun animasyondaki egimini yer duzlemine gore hesaplayip bu yonu
     * olusturuyor) - yani kol ne kadar yukari/asagi/one bakiyorsa, sok
     * dalgasi da TAM o acida, kolun ucundan disari dogru acilir.
     *
     * @param origin yumruk/kol ucunun dunya pozisyonu
     * @param dir    kolun (vurus) yonu, dunya uzayinda (birim olmasi sart degil)
     * @param power  0-1 sarj orani - halka buyuklugunu olcekler
     */
    public static void spawnPunchBurst(Vec3 origin, Vec3 dir, float power) {
        if (dir.lengthSqr() < 1.0e-6) dir = new Vec3(0, 0, 1);
        dir = dir.normalize();

        // Halka duzlemi vurus yonune DIK bakar (right x up ~ dir). dir
        // neredeyse dikeyse (dumduz yukari bir yumruk) dunya-yukarisiyla
        // cross-carpim dejenere olur - bu durumda yatay bir referans eksene
        // (dunya Z'si) dusup ayni sekilde dik bir "sag" ekseni turetiyoruz.
        Vec3 worldUp = new Vec3(0, 1, 0);
        Vec3 right = worldUp.cross(dir);
        if (right.lengthSqr() < 1.0e-4) {
            right = new Vec3(0, 0, 1).cross(dir);
        }
        right = right.normalize();
        Vec3 up = dir.cross(right).normalize();

        float maxRadius = PUNCH_RADIUS_MIN + (PUNCH_RADIUS_MAX - PUNCH_RADIUS_MIN) * clamp01(power);
        long now = System.nanoTime();

        for (int i = 0; i < PUNCH_RING_COUNT; i++) {
            long spawnAt = now + PUNCH_STAGGER_NANOS * i;
            ACTIVE.add(new ShockwaveFx(origin, right, up, maxRadius, spawnAt, PUNCH_DURATION_NANOS,
                    PUNCH_START_RADIUS, PUNCH_RING_THICKNESS, PUNCH_COLOR_R, PUNCH_COLOR_G, PUNCH_COLOR_B));
        }
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (ACTIVE.isEmpty()) return;

        PoseStack poseStack = event.getPoseStack();
        Vec3 camPos = event.getCamera().getPosition();

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        long now = System.nanoTime();
        Tesselator tesselator = Tesselator.getInstance();

        Iterator<ShockwaveFx> it = ACTIVE.iterator();
        while (it.hasNext()) {
            ShockwaveFx fx = it.next();
            long age = now - fx.spawnNanos;
            if (age < 0) continue; // sirasi henuz gelmedi (kademeli baslangic)
            if (age >= fx.durationNanos) {
                ACTIVE.remove(fx);
                continue;
            }

            float t = age / (float) fx.durationNanos; // 0..1
            drawRing(poseStack, tesselator, fx, t);
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        poseStack.popPose();
    }

    private static void drawRing(PoseStack poseStack, Tesselator tesselator, ShockwaveFx fx, float t) {
        // Hizli genisleyip yumusak sonen bir egri: ease-out genisleme, ease-in solma.
        // (kucukten buyuyerek acilma - startRadius -> maxRadius - HER kayit turu
        // icin gecerli, sadece baslangic/kalinlik/renk kayda gore degisiyor.)
        float grow = 1.0f - (1.0f - t) * (1.0f - t);
        float outerR = fx.startRadius + (fx.maxRadius - fx.startRadius) * grow;
        float innerR = Math.max(0.0f, outerR - fx.ringThickness);
        float alpha = (1.0f - t) * (1.0f - t) * 0.85f;

        Matrix4f matrix = poseStack.last().pose();
        BufferBuilder buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);

        for (int i = 0; i <= SEGMENTS; i++) {
            double angle = (Math.PI * 2.0) * i / SEGMENTS;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);

            Vec3 outer = fx.center.add(fx.right.scale(cos * outerR)).add(fx.up.scale(sin * outerR));
            Vec3 inner = fx.center.add(fx.right.scale(cos * innerR)).add(fx.up.scale(sin * innerR));

            buffer.vertex(matrix, (float) outer.x, (float) outer.y, (float) outer.z)
                    .color(fx.colorR, fx.colorG, fx.colorB, alpha).endVertex();
            buffer.vertex(matrix, (float) inner.x, (float) inner.y, (float) inner.z)
                    .color(fx.colorR, fx.colorG, fx.colorB, alpha).endVertex();
        }

        tesselator.end();
    }

    private static float clamp01(float v) { return v < 0f ? 0f : Math.min(1f, v); }
}
