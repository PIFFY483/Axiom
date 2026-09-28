package com.axiom.skill.effect;

import com.axiom.anim.PoseSample;
import com.axiom.anim.pose.DashUppercutPose;
import com.axiom.network.DashShockwaveFxPacket;
import com.axiom.network.DashUppercutFxPacket;
import com.axiom.network.DashWindTrailFxPacket;
import com.axiom.network.ModNetwork;
import com.axiom.network.PunchShockwaveFxPacket;
import com.axiom.network.VacuumWindFxPacket;
import com.axiom.skill.SkillEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
 *
 * BULUNAN BUG (duzeltildi): koni kirma (breakConeInFront) eskiden
 * onImpactMoment() icinde, yani SABIT bir animasyon zamanlamasinda
 * (~IMPACT_DELAY, ~0.4s) tetikleniyordu - karakterin dash'i fiziksel
 * olarak nereye vardigindan tamamen bagimsiz. Sarj yuksekken koni uzunlugu
 * da buyuk hesaplaniyordu, ama hedef yakinsa karakter kisa mesafede
 * duruyordu; sonuc olarak koni gercek vurus noktasindan degil, "olmasi
 * gereken" (animasyonun varsaydigi) bir konumdan aciliyordu. Duzeltme:
 * hedef VARSA koni kirma islemi artik tryImpact() icinde, gercek isabet/
 * iskalama aninda tetikleniyor. Hedef YOKSA (bosa cikilan dash, tryImpact
 * hic cagrilmiyor) koni kirma hala onImpactMoment()'ta kaliyor.
 *
 * IKINCI TUR BUG (duzeltildi): konum duzeltildikten sonra bile koni, hedefe
 * COK yakin mesafede (IMPACT_RANGE=2.2) isabet edildiginde hicbir blok
 * kirmiyordu. Sebep: koni her zaman OYUNCUNUN GOZUNDEN baslayip genisleyerek
 * uzuyordu (koken=sivri uc, yaricap=0) - kisa mesafede bu sivri ucun yaricapi
 * neredeyse sifira yakin kaliyor, hicbir blok merkezine denk gelmiyordu.
 *
 * KARAR (hedefe isabet durumu icin): konumu/yaricapi duzeltmeye calismak
 * (hedefin konumundan baslatma, minimum yaricap vb.) tutarli/kaliteli bir
 * his vermedi - bu yuzden hedefe GERCEKTEN isabet edildiginde koni artik
 * TAMAMEN KALDIRILDI (bkz. tryImpact - hit dalinda breakConeInFront cagrisi
 * yok). Koni sadece BOSA giden yumruklarda (hedef yok ya da hic menzile
 * girilemedi) aciliyor.
 *
 * UCUNCU TUR KARAR: "hedef vardi ama hic menzile girilemedi" (timeout) durumu,
 * hedefin konumuna GORE ARTIK HESAPLANMIYOR - ne koken ne eksen icin. Onceki
 * ara-adimlarda hedefin (son bilinen) konumu koken ya da eksen hesabina
 * karisiyordu, ama oyuncu-hedef mesafesi kucuk kaldiginda ("az farkla
 * ıskalama") bu yine koninin hedefin ustune/altina yakin cikmasina yol
 * aciyordu. Artik bu dal, "hic hedef yoktu" durumuyla (onImpactMoment'in
 * target==null dali) TAMAMEN AYNI: koken = oyuncunun goz pozisyonu, eksen =
 * oyuncunun o anki bakisi - bkz. tryImpact ve onImpactMoment.
 */
public final class DashUppercutEffect implements SkillEffect {
    public static final DashUppercutEffect INSTANCE = new DashUppercutEffect();
    private DashUppercutEffect() {}

    /** chargeRatio 0 olsa bile minik bir etki kalsin diye taban guc orani. */
    private static final float MIN_POWER_FRACTION = 0.25f;

    // YENI: FORWARD_RANGE artik MAX_DASH_DISTANCE ile eslesiyor - onceden 3.5
    // blokta sabitti, yani MAX_DASH_DISTANCE'i (20.0) yukseltsek bile hedef
    // hep ~3.5 blok icinde kilitlendigi icin dash pratikte hep kisa kaliyordu.
    private static final double FORWARD_RANGE = 20.0;     // hedef arama mesafesi (blok)
    // Uzun menzilde daha kolay hedef bulmak icin arama kutusu biraz genisletildi.
    private static final double SEARCH_WIDTH = 3.0;       // arama kutusunun genisligi/yuksekligi (blok)
    private static final double IMPACT_RANGE = 2.2;       // bu mesafeye girince "menzilde" sayilir

    // YENI: chargeRatio artik gercek bir MENZIL belirliyor - az sarjda kisa,
    // tam sarjda MAX_DASH_DISTANCE'a kadar. Onceki haliyle mesafe SADECE
    // hedefin gercek uzakligina bakiyordu, sarj oranini hic kullanmiyordu -
    // bu yuzden tam sarjda bile 20 bloga ulasilmiyordu.
    private static final double MIN_DASH_DISTANCE = 4.0;
    private static final double MAX_DASH_DISTANCE = 20.0;

    // YENI: dash suresi artik SABIT degil, mesafeye gore 0.2s (yakin) ile
    // 0.8s (MAX_DASH_DISTANCE kadar uzak) arasinda olceklenir - yakin hedefe
    // hizli/kisa, uzak hedefe daha uzun sürede ama HER ZAMAN bu iki sinir
    // icinde ulasilir.
    private static final double MIN_TRAVEL_SECONDS = 0.2;
    private static final double MAX_TRAVEL_SECONDS = 0.8;
    // Fiziksel olarak asiri hizlanip duvar/blok icine gomulmeyi engelleyen
    // saf bir guvenlik tavani (mesafe/sure oraniyla normalde bu deger asilmaz).
    private static final double DASH_SPEED_SAFETY_CAP = 2.5; // blok/tick

    private static final double LAUNCH_HORIZONTAL_BASE = 1.3;
    private static final double LAUNCH_HORIZONTAL_MAX = 3.4;
    private static final double LAUNCH_VERTICAL_BASE = 0.7;
    private static final double LAUNCH_VERTICAL_MAX = 2.0;

    private static final float HIT_DAMAGE = 1.0f;

    /** Devam eden bir dash'in sabit hedef hizi, baslangic konumu (DEBUG icin),
     *  hedeflenen mesafe (DEBUG icin) ve ne zaman sonlanacagi. */
    private record ActiveDash(Vec3 velocity, Vec3 startPos, double totalDistance,
                              long endGameTime) {}

    /** Oyuncu basina o an "friction'a karsi zorlanan" dash - onDash'te doldurulur,
     *  tryImpact()'te (hit ya da miss, farketmez) silinir. */
    private static final Map<UUID, ActiveDash> ACTIVE_DASHES = new ConcurrentHashMap<>();

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

    // YENI: yumruk sok dalgasi (bkz. tryImpact -> spawnPunchShockwave) icin,
    // IMPACT_DELAY'in animasyonun KENDI zaman-cizelgesindeki (0..ANIM_LENGTH)
    // karsiligi - PoseAnimationPlayer'daki "elapsed = chargeCap + progress*(length-chargeCap)"
    // ile AYNI formul. Bu sayede DashUppercutPose.sampleRightArm(...) tam
    // vurus anindaki (yumrugun hedefe degdigi karedeki) kol pozunu verir.
    private static final float IMPACT_POSE_TIME = DashUppercutPose.CHARGE_PHASE_END
            + (IMPACT_DELAY / DashUppercutPose.POST_CHARGE_PLAY_DURATION)
            * (ANIM_LENGTH - DashUppercutPose.CHARGE_PHASE_END);

    // Sok dalgasinin dogacagi nokta icin kaba bir "kol ucu" yaklasimi: omuz
    // yuksekligi + vurus yonunde bir miktar ileri/disari kaydirma (uzanan
    // kol+yumruk mesafesi). Tam kemik/bone dunya-pozisyonu hesaplamak yerine
    // bilerek bu basit yaklasim kullanildi - gorsel bir efekt icin yeterli.
    private static final double PUNCH_SHOULDER_HEIGHT_FRACTION = 0.75; // govde yuksekliginin orani
    private static final double PUNCH_ARM_REACH = 0.9; // blok - uzanmis kol+yumruk mesafesi

    // YENI: vurus aninda (hedefe DEGSIN DEGMESIN) karakterin bakis yonunde
    // acilan, sarj oranina gore BUYUYEN koni seklindeki kirma alani.
    // Hem uzunluk hem genislik (yari-aci) sarj oranina gore olceklenir -
    // az sarjda kucuk/dar bir koni, tam sarjda cok daha uzun/genis bir koni.
    private static final double CONE_LENGTH_MIN = 3.0;          // blok - az sarj
    private static final double CONE_LENGTH_MAX = 9.0;          // blok - tam sarj
    private static final double CONE_HALF_ANGLE_MIN_DEG = 12.0; // az sarj - dar koni
    private static final double CONE_HALF_ANGLE_MAX_DEG = 28.0; // tam sarj - genis koni

    // BULUNAN BUG (asil kok neden): koni her zaman OYUNCUNUN GOZUNDEN baslayip
    // genisleyerek uzuyordu (tam basta yaricap = 0, klasik sivri koni ucu).
    // Hedefe COK yakin mesafede (IMPACT_RANGE=2.2 blok) isabet edildiginde,
    // koninin o kisa mesafedeki yaricapi neredeyse sifira yakin kaliyordu -
    // yani hicbir blok merkezine denk gelmeyen igne-ucu gibi ince bir alan.
    // "Bosa" savurmalarda genelde bir duvara/yapiya dogru kosuldugu icin
    // (bastan sona blok dolu bir yuzey) ayni ince koni bile bir seyler
    // yakaliyordu - bu yuzden "hedefe vurunca kirmiyor, bosa sallayinca
    // kiriyor" gibi gorunuyordu.
    //
    // Duzeltme IKI parcali:
    //  1) Hedef VARSA (isabet ya da hedefle-birlikte-iskalama) koninin
    //     KOKENI artik oyuncunun gozunden degil, HEDEFIN GERCEK KONUMUNDAN
    //     baslıyor - boylece "koninin sivri ucu" sorunu tamamen ortadan
    //     kalkiyor, cunku artik vurus noktasinin KENDISI koninin baslangici.
    //     Bu ayni zamanda onceki "koni hedefin cok otesine geciyor" sorununu
    //     da dogal olarak cozuyor - artik mesafeye gore ayri bir sinirlama
    //     GEREKMIYOR, cunku koni zaten dogru noktadan baslayip ileri devam
    //     ediyor (vurusun "takip/follow-through"u gibi).
    //  2) Koninin KOKENINDE (axial=0) bile yaricap sifir OLMUYOR - CONE_MIN_
    //     RADIUS kadar bir taban yaricap her zaman var, boylece vurus
    //     noktasinin TAM cevresindeki bloklar (mesafeye/acidan bagimsiz)
    //     her zaman garanti kiriliyor.
    private static final double CONE_MIN_RADIUS_MIN = 0.8; // blok - az sarj, koken yaricapi
    private static final double CONE_MIN_RADIUS_MAX = 1.6; // blok - tam sarj, koken yaricapi

    // DUZELTME (hedefleme isabeti): eskiden lockTarget() yatay (forwardFlat)
    // bir koridor icindeki EN YAKIN canliyi seciyordu - "bakis merkezine en
    // yakin" degil, "oyuncuya en yakin". Bu yuzden koridor icinde kenarda
    // duran ama oyuncuya yakin bir mob, tam bakilan ama biraz daha uzaktaki
    // mobun onune geciyordu.
    //
    // Skill zaten sadece DUZ (yatay) atiliyor - yukari/asagi bakis dash'i
    // etkilemiyor - o yuzden yon vektoru YINE forwardFlat (yatay), pitch'e
    // dokunulmuyor. Degisen tek sey: adaylar arasinda artik "yatay bakis
    // eksenine en yakin acida olan" kazaniyor (mesafe sadece esit acida
    // ikincil kriter), "en yakin olan" degil.
    private static final double TARGET_CONE_HALF_ANGLE_DEG = 28.0;
    private static final double TARGET_CONE_MIN_COS = Math.cos(Math.toRadians(TARGET_CONE_HALF_ANGLE_DEG));
    private static final double TARGET_DISTANCE_PENALTY = 0.015; // blok basina hafif mesafe cezasi

    @Override
    public LivingEntity lockTarget(ServerPlayer player) {
        Vec3 look = forwardFlat(player); // yatay - dash zaten dikey hareket etmiyor
        Vec3 origin = player.position();
        Vec3 tip = origin.add(look.scale(FORWARD_RANGE));
        AABB searchBox = new AABB(origin, tip).inflate(SEARCH_WIDTH / 2.0, 1.0, SEARCH_WIDTH / 2.0);

        List<LivingEntity> candidates = player.level().getEntitiesOfClass(
                LivingEntity.class, searchBox,
                e -> e != player && e.isAlive() && !e.isSpectator());

        LivingEntity best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (LivingEntity e : candidates) {
            Vec3 toEntityFlat = e.getBoundingBox().getCenter().subtract(origin);
            toEntityFlat = new Vec3(toEntityFlat.x, 0, toEntityFlat.z); // dikeyi at, sadece yatay aci onemli
            double dist = toEntityFlat.length();
            if (dist < 1.0e-4 || dist > FORWARD_RANGE) continue;

            double cos = toEntityFlat.scale(1.0 / dist).dot(look);
            if (cos < TARGET_CONE_MIN_COS) continue; // gorus konisinin disinda

            double score = cos - dist * TARGET_DISTANCE_PENALTY;
            if (score > bestScore) { bestScore = score; best = e; }
        }
        return best;
    }

    @Override
    public void onDash(ServerPlayer player, float chargeRatio, LivingEntity target) {
        Vec3 forward = forwardFlat(player);
        float power = powerOf(chargeRatio);

        // Sarjin izin verdigi azami menzil - az sarj = kisa, tam sarj = 20 blok.
        double chargeReach = lerp(power, MIN_DASH_DISTANCE, MAX_DASH_DISTANCE);

        double travelDistance;
        if (target != null) {
            double distToTarget = player.position().distanceTo(target.position());
            // Hedef menzil ICINDEYSE tam ona kadar git; DISINDAYSA (nadiren -
            // lockTarget de FORWARD_RANGE=20 ile ariyor ama farkli bir tick'te
            // hedef uzaklasmis olabilir) sarjin izin verdigi kadar git.
            travelDistance = Math.min(distToTarget, chargeReach);
        } else {
            // Hedef yok - yine de sarjin tam menzili kadar duz bir sicrama yap.
            travelDistance = chargeReach;
        }

        // Mesafe orani (0 = hemen yaninda, 1 = MAX_DASH_DISTANCE'in tam ucunda)
        // -> 0.2s ile 0.8s arasinda bir seyahat suresi. Yakinsa hizli/kisa,
        // uzaksa daha uzun surede ama HER ZAMAN bu araliktan cikmadan gider.
        double distRatio = travelDistance / MAX_DASH_DISTANCE;
        double travelSeconds = lerp((float) distRatio, MIN_TRAVEL_SECONDS, MAX_TRAVEL_SECONDS);
        long travelTicks = Math.max(1, Math.round(travelSeconds * 20.0));

        // Naive hiz - artik hurtMarked her tick gonderildigi icin (bkz.
        // tickDash) sunucu ile client senkron kaliyor, ekstra bir
        // duzeltme katsayisina gerek kalmamali.
        double v0 = travelDistance / travelTicks; // blok/tick
        v0 = Math.min(v0, DASH_SPEED_SAFETY_CAP);

        Vec3 dash = forward.scale(v0);
        Vec3 current = player.getDeltaMovement();
        // Sadece yatay atilma - dikey hiza (zipla ma) dokunmuyoruz, karakter
        // yerdeyse yerde kaliyor.
        player.setDeltaMovement(current.x + dash.x, current.y, current.z + dash.z);
        player.hurtMarked = true; // hareketi hemen istemciye senkronize et

        // tickDash() bu hizi pencere boyunca HER TICK yeniden zorlayacak -
        // vanilla'nin friction'i (nedeni ne olursa olsun) tek seferlik
        // itkiyi yiyip bitiriyordu, surekli yeniden dayatmak bunu onluyor.
        long endAt = player.level().getGameTime() + travelTicks;
        ACTIVE_DASHES.put(player.getUUID(), new ActiveDash(dash, player.position(), travelDistance, endAt));

        // Ziplama/atilma anindaki hiz hissi icin FOV punch (client-only fx) -
        // sarj oranina gore olcekleniyor (bkz. DashUppercutFxPacket).
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new DashUppercutFxPacket(DashUppercutFxPacket.Stage.LEAP, power));

        // Gecilen yolun uzerine yere-catlak/toz izi birak - dash NE KADAR
        // surerse (travelTicks) iz de o kadar sure yolu takip etsin.
        DashCrackTrail.start(player, (int) travelTicks);

        // Karakterin ARKASINDAN cikan enerji halkasi (ozel render, vanilla
        // particle DEGIL - bkz. ShockwaveFxRenderer) - mesafeye gore 1-3
        // kere art arda patlar, uzun/tam sarjli dash'lerde 3'e kadar cikar.
        int shockwaveCount = shockwaveCountFor(distRatio);
        ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                new DashShockwaveFxPacket(
                        player.getX(), player.getY() + player.getBbHeight() * 0.5, player.getZ(),
                        (float) forward.x, (float) forward.z, power, shockwaveCount));

        // YENI: ucus/leap boyunca (travelSeconds kadar) karakterin etrafinda
        // beliren, geriye dogru akan yari-saydam ruzgar cizgileri - bkz.
        // WindTrailFxRenderer.spawnFlightTrail. Entity ID tasiyor cunku
        // karakter bu sure boyunca hareket halinde, sabit bir nokta degil.
        ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                new DashWindTrailFxPacket(player.getId(), (float) forward.x, (float) forward.z,
                        power, (float) travelSeconds));
    }

    @Override
    public void tickDash(ServerPlayer player) {
        ActiveDash active = ACTIVE_DASHES.get(player.getUUID());
        if (active == null) return;

        long now = player.level().getGameTime();
        if (now >= active.endGameTime()) {
            ACTIVE_DASHES.remove(player.getUUID());
            return;
        }

        // Vanilla'nin bu tick ne kadar yediginden bagimsiz olarak, yatay
        // hizi sabit dash hizina yeniden zorluyoruz.
        Vec3 v = player.getDeltaMovement();
        player.setDeltaMovement(active.velocity().x, v.y, active.velocity().z);
        // BULUNAN BUG: bu satir eksikti - onceki reset'ler sunucu icinde
        // degisiyordu ama client'a HIC senkronize edilmiyordu (sadece
        // onDash'teki ILK setDeltaMovement gercekten paket olarak gidiyordu).
        // Client, bizim "yeniden zorlama" cabamizdan habersiz, kendi
        // friction modeliyle o ilk hizi soendueruyordu - olcumlerdeki
        // tutarli kisa kalis TAM OLARAK bunu isaret ediyordu.
        player.hurtMarked = true;
    }

    @Override
    public boolean tryImpact(ServerPlayer player, LivingEntity target, float chargeRatio, boolean forceIfTimedOut) {
        if (!target.isAlive()) return true; // hedef gitti, beklemeye deger yok

        boolean inRange = player.distanceToSqr(target) <= IMPACT_RANGE * IMPACT_RANGE;

        if (!inRange) {
            // GERI ALINDI (v5): "dashFinished"/erken-karar optimizasyonu
            // (bir onceki turda eklenmisti) KALDIRILDI. Sebep: dash'in SON
            // birkac tick'i cogu zaman oyuncuyu tam da IMPACT_RANGE'e SOKAN
            // kisimdi - erken karar, oyuncu meNZILE TAM GIRECEKKEN hizini
            // kesip "miss" ilan ediyordu (hedef acikca menzildeyken bile
            // firlatma hic olmuyordu - bkz. kullanici geri bildirimi). Koni
            // artik zaten TAMAMEN kaldirildigi icin (bkz. asagisi) o erken
            // karari tetikleyen "gorsel gecikme" sorunu da ortadan kalkti -
            // yani bu optimizasyonun hicbir faydasi kalmadi, sadece riski
            // kaldi. Sonuc: sadece forceIfTimedOut'a (sabit grace penceresi,
            // bkz. impactTimeoutGraceSeconds) guveniyoruz - bu, MENZILE
            // GIRECEK hicbir vurusu asla erken kesmeyen, orijinal DOGRU
            // davranis.
            if (!forceIfTimedOut) return false; // henuz ulasilmadi - son karede tutmaya devam

            // Sure doldu ve HALA menzile girmedi -> gercek bir BOSA SALLAMA (miss).
            // Hedef kacmis/uzaklasmis olabilir - artik hasar/firlatma uygulanmiyor,
            // karakter sadece kendi momentumunu kaybedip toparlaniyor.
            ACTIVE_DASHES.remove(player.getUUID()); // tickDash artik hizi zorlamasin
            Vec3 v = player.getDeltaMovement();
            player.setDeltaMovement(v.x * 0.3, v.y, v.z * 0.3);
            player.hurtMarked = true;

            // YENI: hedefe ulasilamasa (iskalama) bile yumruk yine de
            // "sallandigi" icin yukari dogru yukselen ruzgar tuyu efekti
            // yine tetikleniyor - bkz. VacuumWindFxPacket.
            sendVacuumWind(player, powerOf(chargeRatio));

            // KARAR (v4): kullanicidan gelen geri bildirim uzerine - hedefli
            // ıskalamada (target vardi ama menzile hic girilemedi) artik HICBIR
            // koni/blok kirma efekti TETIKLENMIYOR. Sebep: "iskalama" karari
            // (yukaridaki dashFinished/MISS_DECISION_LEAD mantigi) zaman zaman
            // ERKEN veriliyor ve gercekte MENZILE GIRECEK olan bir vurusu bile
            // yanlislikla "miss" sayabiliyor - boyle bir durumda koni kirmak
            // yanlis/kafa karistirici bir gorsel sinyal verirdi (oyuncu hedefi
            // vurdugunu dusunurken "ıskaladin" efekti gorur). Koni artik
            // SADECE gercek "boş dash" durumunda (lockTarget hic hedef
            // bulamadi, target==null - bkz. onImpactMoment) aciliyor; o
            // durumda yanlis-negatif riski yok, cunku ortada zaten hicbir hedef
            // yok. Hedefli-ama-kacirilan durumun kendi gorsel/oyun-ici geri
            // bildirimi (varsa) daha sonra ayrica ele alinacak.
            return true;
        }

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

        // YENI: karakterin kendi ileri momentumunu kes - aksi halde vurus
        // uygulansa bile oyuncu ayni hizla hedefin/gectigi noktanin
        // otesine kaymaya devam ediyordu ("icinden gecip gitme" hissi).
        ACTIVE_DASHES.remove(player.getUUID()); // tickDash artik hizi zorlamasin
        Vec3 v = player.getDeltaMovement();
        player.setDeltaMovement(v.x * 0.1, v.y, v.z * 0.1);
        player.hurtMarked = true;

        // Vurus ani - screen shake + hitstop (client-only fx, bkz. DashUppercutFxPacket) -
        // sarj oranina gore olcekleniyor.
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new DashUppercutFxPacket(DashUppercutFxPacket.Stage.IMPACT, power));

        // YENI: yumruk hedefe TAM DEGDIGI anda sag kolun ucundan cikan,
        // kucukten buyuyerek genisleyen sok dalgasi - kolun O ANKI (yere
        // gore) egimiyle AYNI acida disari acilir (bkz. spawnPunchShockwave).
        spawnPunchShockwave(player, forward, power);

        // YENI: isabet aninda da (metal sok dalgasina EK olarak) yukari
        // dogru yukselen ruzgar tuyu efekti - bkz. VacuumWindFxPacket.
        sendVacuumWind(player, power);

        // KARAR: hedefe GERCEKTEN isabet edildiyse koni artik hic acilmiyor.
        // Konumu/yaricapi tutarli hale getirmeye ugrasmak (hedefin konumundan
        // baslatma, minimum yaricap vb.) beklenen kaliteyi vermedi - bu yuzden
        // basitce kaldirildi. Koni SADECE bosa giden (hedefe degmeyen)
        // yumruklarda aciliyor - bkz. asagidaki onImpactMoment/tryImpact'in
        // miss dallari.
        return true;
    }

    @Override
    public void onImpactMoment(ServerPlayer player, float chargeRatio, LivingEntity target) {
        // Hedef VARSA koni kirma islemini burada TETIKLEME: bu an sabit bir
        // animasyon zamanlamasidir, karakterin dash'i fiziksel olarak nereye
        // vardigindan bagimsizdir. O durumda koni, tryImpact() gercek isabet/
        // iskalama aninda (hedefin fiilen bulundugu konumda) kiriliyor.
        //
        // Hedef YOKSA (lockTarget hic bulamadi) tryImpact() hic cagrilmayacak
        // (bkz. SkillTriggerScheduler), yani bu efekt icin TEK tetiklenme
        // firsati budur - burada, oyuncunun kendi konumundan kiriliyor, ve
        // "bosa savurma" hissi icin duman efekti ekleniyor.
        if (target == null) {
            Vec3 origin = player.getEyePosition();
            breakConeInFront(player, powerOf(chargeRatio), origin, player.getLookAngle());
            spawnWhiffSmoke(player, origin);
            // YENI: hic hedef olmayan "bosa" yumrukta da ruzgar tuyu efekti
            // cikar - bkz. VacuumWindFxPacket.
            sendVacuumWind(player, powerOf(chargeRatio));
        }
    }

    /**
     * Yumruk anindaki (isabet/iskalama/hedefsiz - farketmez) yukari dogru
     * yukselen ruzgar tuyu efektini tetikler - bkz. VacuumWindFxPacket.
     * Sabit bir origin GONDERMIYOR, entity ID gonderiyor: efekt client'ta
     * 2.5 saniye boyunca karakterin GUNCEL pozisyonunu takip ederek
     * periyodik tetiklenir (bkz. WindTrailFxRenderer.spawnVacuumEmitter) -
     * boylece animasyon RETURNING fazina gecerken tek bir anlik pozisyonun
     * yakalanmasindan kaynaklanan "yanlis yerden cikma" sorunu olmuyor.
     */
    private static void sendVacuumWind(ServerPlayer player, float power) {
        ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                new VacuumWindFxPacket(player.getId(), power));
    }

    /**
     * "Bosa sallama" (miss - hedef yok ya da hic menzile girmedi) hissini
     * pekistirmek icin kamp atesi dumanina benzer, dagilan bir toz/is
     * bulutu (client-only, ServerLevel#sendParticles otomatik yakindaki
     * oyunculara yayinlar - ozel bir paket gerekmiyor).
     */
    private static void spawnWhiffSmoke(ServerPlayer player, Vec3 origin) {
        player.serverLevel().sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                origin.x, origin.y, origin.z, 14, 0.5, 0.4, 0.5, 0.01);
    }


    private static void breakConeInFront(ServerPlayer player, float power, Vec3 origin, Vec3 axisIn) {
        ServerLevel level = player.serverLevel();

        Vec3 axis = axisIn;
        if (axis == null || axis.lengthSqr() < 1.0e-6) axis = new Vec3(0, 0, 1);
        axis = axis.normalize();

        double length = lerp(power, CONE_LENGTH_MIN, CONE_LENGTH_MAX);
        double minRadius = lerp(power, CONE_MIN_RADIUS_MIN, CONE_MIN_RADIUS_MAX);

        double halfAngleRad = Math.toRadians(lerp(power, CONE_HALF_ANGLE_MIN_DEG, CONE_HALF_ANGLE_MAX_DEG));
        double tanHalfAngle = Math.tan(halfAngleRad);
        double maxRadius = Math.max(minRadius, length * tanHalfAngle);

        AABB bounds = new AABB(origin, origin.add(axis.scale(length))).inflate(maxRadius, maxRadius, maxRadius);

        int minX = Mth.floor(bounds.minX), maxX = Mth.floor(bounds.maxX);
        int minY = Mth.floor(bounds.minY), maxY = Mth.floor(bounds.maxY);
        int minZ = Mth.floor(bounds.minZ), maxZ = Mth.floor(bounds.maxZ);

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    cursor.set(x, y, z);
                    BlockState state = level.getBlockState(cursor);
                    if (state.isAir()) continue;

                    Vec3 rel = Vec3.atCenterOf(cursor).subtract(origin);
                    double axial = rel.dot(axis);
                    if (axial < 0 || axial > length) continue; // koninin ONUNDE/gerisinde degil

                    double radiusHere = Math.max(minRadius, axial * tanHalfAngle);
                    double perpSq = rel.lengthSqr() - axial * axial;
                    if (perpSq > radiusHere * radiusHere) continue; // koninin disinda

                    // Kirilamaz bloklari (bedrock, barrier, komut bloklari vb. -
                    // negatif sertlik dondururler) atla.
                    if (state.getDestroySpeed(level, cursor) < 0f) continue;

                    level.destroyBlock(cursor, true, player);
                }
            }
        }
    }


    /**
     * Vurus anindaki (tryImpact basarili donunce) sag kol sok dalgasini
     * hesaplar ve tetikler.
     *
     * Kolun DUNYA UZAYINDAKI yonunu bulmak icin: DashUppercutPose'un o anki
     * (IMPACT_POSE_TIME) sag kol ornegindeki kombine (govde+kol) rotasyon
     * quaternion'unu alip, kolun varsayilan "asagi sarkma" yonunu (model
     * uzayinda +Y, bkz. DashUppercutPose - "y-ASAGI, piksel") bu quaternion
     * ile donduruyoruz. Sonucun dikey bileseni bize kolun YERE GORE ACISINI
     * (pitch) veriyor - animasyonun kendi (spin iceren) govde yaw'ini
     * cozumlemeye calismak yerine, bu acinin YATAY bilesenini oyuncunun
     * GERCEK bakis yonune (forward, zaten dogru ve tutarli) uyguluyoruz.
     * Boylece hem "kol ne acidaysa sok dalgasi da o acida" hem de "oyuncu
     * nereye bakiyorsa vurus o yone gider" saglanmis oluyor.
     */
    private static void spawnPunchShockwave(ServerPlayer player, Vec3 forward, float power) {
        PoseSample armSample = DashUppercutPose.INSTANCE.sampleRightArm(IMPACT_POSE_TIME);
        Quaternionf armRot = armSample.rot();

        Vec3 punchDir;
        if (armRot != null) {
            // Model uzayinda kolun varsayilan (rotasyonsuz) yonu: omuzdan
            // asagi (y-ASAGI kuralinda +Y). Kombine rotasyonla dondurulunce
            // kolun o anki fiili egimini (yere gore acisini) verir.
            Vector3f animDir = armRot.transform(new Vector3f(0f, 1f, 0f));
            double verticalUp = -animDir.y; // model Y-asagi -> dunya Y-yukari
            double horizMag = Math.sqrt(animDir.x * animDir.x + animDir.z * animDir.z);
            double pitch = Math.atan2(verticalUp, horizMag); // kolun yere gore acisi (radyan)

            double cosP = Math.cos(pitch);
            double sinP = Math.sin(pitch);
            punchDir = new Vec3(forward.x * cosP, sinP, forward.z * cosP);
            if (punchDir.lengthSqr() < 1.0e-6) punchDir = forward;
            else punchDir = punchDir.normalize();
        } else {
            punchDir = forward; // beklenmedik durumda (rot yoksa) duz ileri
        }

        double shoulderHeight = player.getBbHeight() * PUNCH_SHOULDER_HEIGHT_FRACTION;
        Vec3 shoulder = player.position().add(0, shoulderHeight, 0);
        Vec3 origin = shoulder.add(punchDir.scale(PUNCH_ARM_REACH));

        ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                new PunchShockwaveFxPacket(origin.x, origin.y, origin.z,
                        (float) punchDir.x, (float) punchDir.y, (float) punchDir.z, power));
    }

    @Override public float dashDelaySeconds() { return DASH_DELAY; }
    @Override public float impactDelaySeconds() { return IMPACT_DELAY; }
    // Onceki deger (0.35s) MAX_TRAVEL_SECONDS=0.8'i karsilamiyordu: dash
    // DASH_DELAY (~0.1s) sonra basliyor, en uzak hedefte varisa kadar 0.8s
    // daha gerekebilir (~0.9s toplam) - IMPACT_DELAY (~0.4s, animasyona
    // kilitli) + grace bu toplami karsilamali, o yuzden grace genisletildi.
    @Override public float impactTimeoutGraceSeconds() { return 0.6f; }

    private static float powerOf(float chargeRatio) {
        return MIN_POWER_FRACTION + (1.0f - MIN_POWER_FRACTION) * clamp01(chargeRatio);
    }

    // Mesafe orani (0 = MIN_DASH_DISTANCE civari, 1 = MAX_DASH_DISTANCE'in
    // tam ucu) -> 1 ile 3 arasi halka sayisi. Kisa dash = tek halka, uzun/
    // tam sarjli dash = 3 halka art arda.
    private static int shockwaveCountFor(double distRatio) {
        double r = Math.max(0.0, Math.min(1.0, distRatio));
        int count = 1 + (int) Math.round(2.0 * r);
        return Math.max(1, Math.min(3, count));
    }

    private static Vec3 forwardFlat(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 f = new Vec3(look.x, 0, look.z);
        return f.lengthSqr() > 1.0e-6 ? f.normalize() : new Vec3(0, 0, 1);
    }

    private static double lerp(float t, double a, double b) { return a + (b - a) * t; }
    private static float clamp01(float v) { return v < 0f ? 0f : Math.min(1f, v); }
}