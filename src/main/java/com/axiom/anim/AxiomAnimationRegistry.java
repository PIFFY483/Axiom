package com.axiom.anim;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * assets/axiom/animations/*.json altindaki tum Blockbench animasyonlarini
 * yukler. Resource pack / F3+T ile yeniden yuklenince otomatik guncellenir.
 *
 * Dosya ADLARI (path) sadece ascii kucuk harf, rakam, "_" "-" "." "/" icerebilir
 * (ResourceLocation kurali) - "yukarı vuruş.json" gibi Turkce karakterli isim
 * OLMAZ, dosyayi "yukari_vurus.json" gibi kaydet. JSON icindeki animasyon adi
 * ("yukarı vuruş") ise sadece bizim kendi string anahtarimiz oldugu icin
 * serbest, istedigin gibi kalabilir.
 */
public class AxiomAnimationRegistry implements ResourceManagerReloadListener {

    public static final AxiomAnimationRegistry INSTANCE = new AxiomAnimationRegistry();

    private static final String FOLDER = "animations";

    private final Map<String, BBAnimation> animations = new HashMap<>();

    public BBAnimation get(String name) {
        return animations.get(name);
    }

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        Map<String, BBAnimation> loaded = new HashMap<>();

        Map<ResourceLocation, Resource> found = manager.listResources(
                FOLDER, loc -> loc.getPath().endsWith(".json"));

        for (Map.Entry<ResourceLocation, Resource> entry : found.entrySet()) {
            try (InputStream stream = entry.getValue().open()) {
                loaded.putAll(BBAnimationLoader.load(stream));
            } catch (Exception e) {
                System.err.println("Axiom: animasyon yuklenemedi (" + entry.getKey() + "): " + e.getMessage());
            }
        }

        animations.clear();
        animations.putAll(loaded);

        System.out.println("[Axiom] Animasyon yuklendi, toplam: " + animations.size()
                + " isimler: " + animations.keySet());
    }
}
