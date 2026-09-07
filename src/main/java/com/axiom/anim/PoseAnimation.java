package com.axiom.anim;

/**
 * Elle (hardcoded) yazilmis TEK BIR poz/animasyon icin ortak sozlesme.
 * BBAnimation + BBAnimationLoader'in (JSON okuma) yerini alir.
 *
 * Her poz kendi .java dosyasinda kendi float[][] keyframe tablolarini tutar
 * ve bu arayuzu implement eder. PoseAnimationPlayer, hangi poz oldugunu hic
 * bilmeden sadece bu 6 metodu cagirarak zamani pozla eslestirir.
 *
 * length() -> animasyonun toplam suresi (saniye). PoseAnimationPlayer bu
 * degeri "RELEASING fazi bitti mi" kontrolu icin kullanir.
 */
public interface PoseAnimation {

    /** Animasyonun toplam uzunlugu, saniye cinsinden. */
    float length();

    PoseSample sampleBody(float t);
    PoseSample sampleHead(float t);
    PoseSample sampleRightArm(float t);
    PoseSample sampleLeftArm(float t);
    PoseSample sampleRightLeg(float t);
    PoseSample sampleLeftLeg(float t);
}
