package com.axiom.anim;

import net.minecraft.client.model.geom.ModelPart;
import org.joml.Quaternionf;
import java.util.IdentityHashMap;
import java.util.Map;

/** ModelPart -> bu kare icin uygulanacak WORLD rotasyon quaternion'u (Euler bypass). */
public final class PoseQuatCache {
    private PoseQuatCache() {}
    private static final Map<ModelPart, Quaternionf> CACHE = new IdentityHashMap<>();
    public static void set(ModelPart part, Quaternionf q) { CACHE.put(part, q); }
    public static Quaternionf get(ModelPart part) { return CACHE.get(part); }
    public static void clear(ModelPart part) { CACHE.remove(part); }
}