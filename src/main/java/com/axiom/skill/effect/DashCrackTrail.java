package com.axiom.skill.effect;

import com.axiom.Axiom;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Dash sirasinda gecilen yolun uzerindeki bloklara UC efekt birden uygular:
 *
 *  1) Yuzeydeki ILERLEYEN CATLAK DOKUSU (X seklindeki overlay, vanilla'nin
 *     madencilik yaparken kullandigi "destroy stage" texture'i) -
 *     Level#destroyBlockProgress(breakerId, pos, stage) ile - blok GERCEKTEN
 *     KIRILMIYOR, sadece gorsel overlay. Kisa bir sure sonra stage=-1
 *     gonderilerek temizleniyor (yoksa gectigin her yer kalici catlak gibi
 *     kalirdi). breakerId sadece client'in ayni "kirilma"yi takip edebilmesi
 *     icin bir anahtar, gercek bir entity'ye baglanmasi gerekmiyor.
 *  2) Ucusan kirinti particle'lari (vanilla ParticleTypes.BLOCK) - blogun o
 *     anki BlockState texture'inden otomatik uretiliyor, ozel bir tanim
 *     gerekmez.
 *  3) YENI: oyuncunun TAM ARKASINDAN yukselen duman (ParticleTypes.CAMPFIRE_
 *     COSY_SMOKE) - bkz. spawnTrailingSmoke. Bu efekt aslinda tesadufen (2)
 *     kirinti particle'larinin "duman gibi" gorunmesinden ilham alindi;
 *     kullanici bu hissi begenince resmi/surekli bir duman izi olarak eklendi.
 *     Catlak orneklemesinden (SAMPLE_STEP) AYRI, her tick sadece BIR kere
 *     tetiklenir - yoksa hizli dash'lerde ara-adim sayisina gore katlanip
 *     asiri yogun olurdu.
 *
 * DashUppercutEffect.onDash() -> DashCrackTrail.start(player, travelTicks)
 * ile baslar, dash NE KADAR surerse o kadar tick boyunca oyuncunun o anki
 * pozisyonuna (yani dash sirasinda GERCEKTEN gecilen yola) ucu de basilir.
 *
 * NOT: dash hizi tick basina 1 bloktan fazla olabildigi icin (bkz.
 * DashUppercutEffect.v0), sadece "bu tick'teki tek pozisyon"a basmak
 * aralarda blok atlanmasina (her ~4 blokta 1 bosluk) sebep oluyordu.
 * Bunun icin onceki tick'teki pozisyondan simdikine kadar ara noktalar
 * (LAST_POS + interpolateSteps) orneklenip hepsine catlak basiliyor
 * (duman icin bu orneklemeye gerek yok, bkz. yukaridaki (3)).
 */
@Mod.EventBusSubscriber(modid = Axiom.MOD_ID)
public class DashCrackTrail {

    /** 0-9 arasi catlak asamasi. Orta seride en yogun, yan seritlerde daha
     *  hafif basiliyor ki "yol" ortadan disariya dogru incelen bir gorunum
     *  alsin (onceden ucu de ayni stage=7 kullanip esit kalinlikta duruyordu). */
    private static final int CENTER_STAGE = 8;
    private static final int SIDE_STAGE = 3;
    /** Catlak overlay'i kac tick sonra temizlensin - 100 tick = 5 saniye,
     *  boylece izi bir sure durup sonra kayboluyor. */
    private static final int CRACK_VISIBLE_TICKS = 100;
    private static final int PARTICLES_PER_TICK = 12;
    private static final double SPREAD_XZ = 0.30;
    /** Ara nokta ornekleme araligi (blok) - dash tick basina bundan hizli
     *  giderse (bkz. yukaridaki NOT) araya ek ornekler eklenir. */
    private static final double SAMPLE_STEP = 0.5;

    // YENI: kullanicinin fark ettigi/begendigi "duman" hissi aslinda catlak
    // izindeki ucusan kirinti particle'lariydi (ParticleTypes.BLOCK) - bunu
    // RESMILESTIRIP dash boyunca izin uzerinde, oyuncunun TAM arkasindan
    // yukselen gercek bir duman efekti olarak ekliyoruz (her tick bir kere,
    // catlak orneklemesinden BAGIMSIZ - yoksa hizli dash'lerde ara-adim
    // sayisi kadar katlanip asiri yogun/pahali olurdu).
    private static final int SMOKE_PARTICLES_PER_TICK = 3;
    private static final double SMOKE_XZ_SPREAD = 0.22;

    private static final Map<UUID, Integer> ACTIVE = new ConcurrentHashMap<>();
    // Onceki tick'teki pozisyon - ara noktalari interpolasyonla bulabilmek icin.
    private static final Map<UUID, Vec3> LAST_POS = new ConcurrentHashMap<>();
    // Her catlak icin benzersiz breakerId - gercek oyuncu ID'leriyle cakismasin
    // diye yuksek bir degerden baslatiliyor.
    private static final AtomicInteger NEXT_BREAKER_ID = new AtomicInteger(1_000_000);
    private static final List<PendingClear> PENDING_CLEARS = new CopyOnWriteArrayList<>();

    private DashCrackTrail() {}

    /** Bir catlagin ne zaman (hangi tick'te) temizlenecegini tutan mutable kayit. */
    private static final class PendingClear {
        final ServerLevel level;
        final int breakerId;
        final BlockPos pos;
        int ticksLeft;

        PendingClear(ServerLevel level, int breakerId, BlockPos pos, int ticksLeft) {
            this.level = level;
            this.breakerId = breakerId;
            this.pos = pos;
            this.ticksLeft = ticksLeft;
        }
    }

    /** DashUppercutEffect.onDash() tarafindan cagrilir.
     *  @param ticks dash'in fiilen ne kadar surecegi (tick) - iz TAM bu kadar
     *               sure boyunca birakilir. Onceden sabit TRAIL_TICKS=6
     *               kullaniliyordu, ama dash artik mesafeye gore 4-16 tick
     *               surebiliyor - sabit deger uzun dash'lerde izin yolun
     *               cok gerisinde erken kesilmesine sebep oluyordu. */
    public static void start(ServerPlayer player, int ticks) {
        ACTIVE.put(player.getUUID(), Math.max(1, ticks));
        // Ilk tick'te "onceki pozisyon" olarak simdiki pozisyonu koyuyoruz,
        // yoksa dash'ten ONCEKI konumdan bir anda buraya "interpolasyon"
        // yapip gereksiz/yanlis bir iz cizilir.
        LAST_POS.put(player.getUUID(), player.position());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (ACTIVE.isEmpty() && PENDING_CLEARS.isEmpty()) return;

        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        // 1) Aktif dash'ler icin bir sonraki adima catlak bas.
        Iterator<Map.Entry<UUID, Integer>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> entry = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());

            if (player == null || player.isRemoved()) {
                it.remove();
                LAST_POS.remove(entry.getKey());
                continue;
            }

            spawnCrackOverlay(player);

            int ticksLeft = entry.getValue() - 1;
            if (ticksLeft <= 0) {
                it.remove();
                LAST_POS.remove(entry.getKey());
            } else {
                entry.setValue(ticksLeft);
            }
        }

        // 2) Suresi dolan catlaklari temizle (stage=-1 -> overlay kalkar).
        Iterator<PendingClear> clearIt = PENDING_CLEARS.iterator();
        while (clearIt.hasNext()) {
            PendingClear pending = clearIt.next();
            pending.ticksLeft--;
            if (pending.ticksLeft <= 0) {
                pending.level.destroyBlockProgress(pending.breakerId, pending.pos, -1);
                PENDING_CLEARS.remove(pending);
            }
        }
    }

    private static void spawnCrackOverlay(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return;

        Vec3 current = player.position();
        Vec3 last = LAST_POS.getOrDefault(player.getUUID(), current);
        LAST_POS.put(player.getUUID(), current);

        // YENI: her tick bir kere, oyuncunun TAM ARKASINDAN (hareket yonunun
        // tersinden) yukselen duman - catlak orneklemesinden ayri tutuluyor
        // ki hizli dash'lerdeki ara-adim sayisina gore katlanmasin.
        spawnTrailingSmoke(level, player, current, last);

        // Dash tick basina 1 bloktan fazla gidebildigi icin (bkz. sinif
        // basindaki NOT), onceki tick'ten simdikine kadar ara noktalari da
        // orneklememiz lazim - yoksa hizli dash'lerde araya bosluk giriyordu.
        double dist = last.distanceTo(current);
        int steps = Math.max(1, (int) Math.ceil(dist / SAMPLE_STEP));

        for (int s = 1; s <= steps; s++) {
            Vec3 sample = last.lerp(current, (double) s / steps);
            boolean lastSample = (s == steps);

            List<BlockPos> centerLeftRight = trailPositions(player, sample);
            BlockPos center = centerLeftRight.get(0);
            BlockPos left = centerLeftRight.get(1);
            BlockPos right = centerLeftRight.get(2);

            applyCrackAt(level, center, CENTER_STAGE, lastSample);
            applyCrackAt(level, left, SIDE_STAGE, lastSample);
            applyCrackAt(level, right, SIDE_STAGE, lastSample);
        }
    }

    /**
     * Oyuncunun TAM ARKASINDAN (o anki hareket yonunun tersi) yukselen
     * duman - "bosa kaldiginda" degil, dash SIRASINDA yol boyunca surekli.
     * Onceki/simdiki pozisyon farkindan hareket yonu cikartiliyor; hareket
     * yoksa (ilk tick) oyuncunun bakis yonunun tersi kullanilir.
     */
    private static void spawnTrailingSmoke(ServerLevel level, ServerPlayer player, Vec3 current, Vec3 last) {
        Vec3 moveDir = current.subtract(last);
        if (moveDir.lengthSqr() < 1.0e-6) {
            Vec3 look = player.getLookAngle();
            moveDir = new Vec3(-look.x, 0, -look.z);
        } else {
            moveDir = new Vec3(moveDir.x, 0, moveDir.z);
        }
        if (moveDir.lengthSqr() < 1.0e-6) moveDir = new Vec3(0, 0, 1);
        Vec3 behind = moveDir.normalize().scale(-0.6);

        Vec3 smokePos = current.add(behind).add(0, 0.15, 0);
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                smokePos.x, smokePos.y, smokePos.z,
                SMOKE_PARTICLES_PER_TICK, SMOKE_XZ_SPREAD, 0.05, SMOKE_XZ_SPREAD, 0.01);
    }

    /** Tek bir pozisyona catlak overlay'i (ve istenirse kirinti particle'i) basar. */
    private static void applyCrackAt(ServerLevel level, BlockPos pos, int stage, boolean withParticles) {
        BlockState ground = level.getBlockState(pos);
        if (ground.isAir()) return;

        // 1) Yuzeydeki ilerleyen catlak dokusu (destroy-stage overlay).
        int breakerId = NEXT_BREAKER_ID.getAndIncrement();
        level.destroyBlockProgress(breakerId, pos, stage);
        PENDING_CLEARS.add(new PendingClear(level, breakerId, pos, CRACK_VISIBLE_TICKS));

        // 2) Ucusan kirinti particle'lari - sadece tick'in son (gercek)
        // pozisyonunda gonderiliyor, yoksa interpolasyon ara adimlari
        // particle sayisini gereksiz yere katlıyordu.
        if (withParticles) {
            BlockParticleOption option = new BlockParticleOption(ParticleTypes.BLOCK, ground);
            level.sendParticles(option,
                    pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5,
                    PARTICLES_PER_TICK,
                    SPREAD_XZ, 0.02, SPREAD_XZ,
                    0.06);
        }
    }

    /**
     * Sadece ayak altindaki degil, oyuncunun bakis/hareket yonune gore SAG
     * ve SOL komsu bloklari da dondurur - boylece iz tek karelik bir cizgi
     * degil, 3 blok genisliginde bir "yol" gibi gorunur.
     * @param samplePos yol uzerinde ornek alinan gercek dunya pozisyonu
     *                   (interpolasyon ara noktasi olabilir, canli
     *                   player.blockPosition() degil).
     * @return [center, left, right] sirasiyla.
     */
    private static List<BlockPos> trailPositions(ServerPlayer player, Vec3 samplePos) {
        BlockPos center = BlockPos.containing(samplePos.x, samplePos.y, samplePos.z).below();

        Vec3 look = player.getLookAngle();
        Vec3 forward = new Vec3(look.x, 0, look.z);
        if (forward.lengthSqr() < 1.0e-6) forward = new Vec3(0, 0, 1);
        forward = forward.normalize();

        // Forward'i 90 derece dondurerek "sag" vektorunu bul, en yakin
        // blok komsuluguna yuvarla (-1/0/1).
        int rx = (int) Math.round(-forward.z);
        int rz = (int) Math.round(forward.x);

        return List.of(center, center.offset(-rx, 0, -rz), center.offset(rx, 0, rz));
    }
}

