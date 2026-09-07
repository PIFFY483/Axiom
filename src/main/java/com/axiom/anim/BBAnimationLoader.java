package com.axiom.anim;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Blockbench'ten disa aktarilmis "generic model" animasyon json'larini okur.
 * (Senin paylastigin "yukari vurus.json" formati.)
 *
 * NOT: Sadece sabit sayisal keyframe degerlerini (arrays) destekler.
 * Molang ifadeleri (ornek: "q.anim_time * 2") veya bezier/catmullrom
 * easing ayarlari desteklenmez, hepsi lineer interpolasyonla oynatilir.
 */
public class BBAnimationLoader {

    public static Map<String, BBAnimation> load(InputStream stream) {
        Map<String, BBAnimation> result = new LinkedHashMap<>();

        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonObject animations = root.getAsJsonObject("animations");
            if (animations == null) return result;

            for (Map.Entry<String, JsonElement> entry : animations.entrySet()) {
                String animName = entry.getKey();
                JsonObject animObj = entry.getValue().getAsJsonObject();

                BBAnimation anim = new BBAnimation();
                anim.name = animName;
                anim.length = animObj.has("animation_length")
                        ? animObj.get("animation_length").getAsFloat() : 0f;

                if (animObj.has("loop")) {
                    String loop = animObj.get("loop").getAsString();
                    anim.loopMode = switch (loop) {
                        case "loop" -> BBAnimation.LoopMode.LOOP;
                        case "hold_on_last_frame" -> BBAnimation.LoopMode.HOLD_ON_LAST_FRAME;
                        default -> BBAnimation.LoopMode.ONCE;
                    };
                }

                JsonObject bones = animObj.getAsJsonObject("bones");
                if (bones != null) {
                    for (Map.Entry<String, JsonElement> boneEntry : bones.entrySet()) {
                        String boneName = boneEntry.getKey();
                        JsonObject boneObj = boneEntry.getValue().getAsJsonObject();

                        BBBoneAnimation boneAnim = new BBBoneAnimation();
                        readChannel(boneObj, "rotation", boneAnim.rotation);
                        readChannel(boneObj, "position", boneAnim.position);
                        readChannel(boneObj, "scale", boneAnim.scale);

                        anim.bones.put(boneName, boneAnim);
                    }
                }

                result.put(animName, anim);
            }
        } catch (Exception e) {
            throw new RuntimeException("Blockbench animasyonu okunamadi", e);
        }

        return result;
    }

    private static void readChannel(JsonObject boneObj, String channelName, BBTrack track) {
        if (!boneObj.has(channelName)) return;
        JsonObject channel = boneObj.getAsJsonObject(channelName);

        for (Map.Entry<String, JsonElement> kf : channel.entrySet()) {
            float time;
            try {
                time = Float.parseFloat(kf.getKey());
            } catch (NumberFormatException ex) {
                continue; // beklenmeyen anahtar, atla
            }

            JsonElement valueEl = kf.getValue();
            JsonArray arr;
            if (valueEl.isJsonArray()) {
                arr = valueEl.getAsJsonArray();
            } else if (valueEl.isJsonObject() && valueEl.getAsJsonObject().has("post")) {
                // bazi Blockbench exportlari {"post": [...], "lerp_mode": "..."} kullanir
                arr = valueEl.getAsJsonObject().getAsJsonArray("post");
            } else {
                continue; // ornegin bir molang string ifadesi - desteklenmiyor
            }

            if (arr == null || arr.size() < 3) continue;
            if (!arr.get(0).isJsonPrimitive() || !arr.get(1).isJsonPrimitive() || !arr.get(2).isJsonPrimitive()) {
                continue; // molang string vs. iceren eksen - desteklenmiyor
            }

            track.put(time, new BBVector(
                    arr.get(0).getAsFloat(),
                    arr.get(1).getAsFloat(),
                    arr.get(2).getAsFloat()
            ));
        }
    }
}
