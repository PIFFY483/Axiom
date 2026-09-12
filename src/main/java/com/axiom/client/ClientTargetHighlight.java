package com.axiom.client;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Client-only "bu entity ID'si su an glow ile vurgulanmali" seti.
 *
 * NEDEN entity.setGlowingTag() YETMEDI: Entity.isCurrentlyGlowing() client
 * tarafinda calisirken hasGlowingTag alanini DEGIL, sunucudan senkronize
 * edilen SHARED FLAG'i (DATA_SHARED_FLAGS_ID, bit 6) okuyor. Client'ta
 * setGlowingTag(true) cagirmak o flag'i degistirmiyor - sadece "sunucudaysam
 * true don" dalindaki bir alani set edip hemen ardindan tekrar
 * getSharedFlag(6)'a gore eziyor, yani client'ta pratikte hicbir sey
 * degismiyor. Bu yuzden glow gorunmuyordu.
 *
 * Cozum: Minecraft.shouldEntityAppearGlowing(Entity) client-only bir
 * metod - buraya mixin ile giripbu id setini sorgulayarak gercek/senkronize
 * bir entity-data flag'ine hic ihtiyac duymadan glow'u zorluyoruz.
 */
public final class ClientTargetHighlight {
    private ClientTargetHighlight() {}

    private static final Set<Integer> HIGHLIGHTED = Collections.synchronizedSet(new HashSet<>());

    public static void set(int entityId, boolean highlighted) {
        if (highlighted) HIGHLIGHTED.add(entityId);
        else HIGHLIGHTED.remove(entityId);
    }

    public static boolean isHighlighted(int entityId) {
        return HIGHLIGHTED.contains(entityId);
    }
}
