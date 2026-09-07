package com.axiom.anim;

import java.util.HashMap;
import java.util.Map;

/**
 * Client'ta gorunen her varlik (entity id) icin ayri bir AxiomAnimationPlayer
 * tutar. Sunucudan gelen ChargeSyncPacket burayi gunceller, mixin de burdan
 * okuyup pozu uygular.
 */
public class ClientChargeStates {
    public static final ClientChargeStates INSTANCE = new ClientChargeStates();

    // Test ettigin animasyon + "hazir" sayilacagi sure (saniye).
    // NOT: bu isim ascii olmali - Turkce karakterli isimlerde Windows'ta
    // javac ile JSON okuyucusu farkli kodlama kullanabiliyor, string
    // birebir eslesmeyip animasyon hic bulunamiyordu.
    private static final String ANIMATION_NAME = "yukari_vurus";
    private static final float CHARGE_CAP_SECONDS = 2.0f;

    private final Map<Integer, AxiomAnimationPlayer> players = new HashMap<>();

    public AxiomAnimationPlayer get(int entityId) {
        return players.get(entityId);
    }

    public void startCharging(int entityId) {
        players.computeIfAbsent(entityId, id -> new AxiomAnimationPlayer(ANIMATION_NAME, CHARGE_CAP_SECONDS))
                .startCharging();
    }

    public void release(int entityId) {
        AxiomAnimationPlayer player = players.get(entityId);
        if (player != null) player.release();
    }
}
