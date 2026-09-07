package com.axiom.anim;

import java.util.LinkedHashMap;
import java.util.Map;

public class BBAnimation {

    public enum LoopMode {
        ONCE,
        LOOP,
        HOLD_ON_LAST_FRAME
    }

    public String name;
    public float length; // saniye (animation_length)
    public LoopMode loopMode = LoopMode.ONCE;
    public final Map<String, BBBoneAnimation> bones = new LinkedHashMap<>();

    public BBBoneAnimation bone(String boneName) {
        return bones.get(boneName);
    }
}
