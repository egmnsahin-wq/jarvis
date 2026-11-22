package com.jarvis.assistant.ai

/** Hata durumlarında her seferinde aynı cümleyi söylememek için çeşitli, doğal ifadeler. */
object ErrorVoice {
    // Not: limit cümleleri IntentSchema.FAILURE_MARKERS ile tanınsın diye hep aynı ifadeyle başlar.
    fun limitReached(): String = listOf(
        "Ücretsiz kullanım limitime takıldım, şimdilik daha fazla düşünemiyorum. Biraz dinlendikten sonra tekrar seninle konuşmak isterim.",
        "Ücretsiz kullanım limitime takıldım galiba. Bir süre sonra tekrar dener misin, seninle konuşmaya devam etmek isterim.",
        "Ücretsiz kullanım limitime takıldım, biraz mola vermem lazım. Sonra yine sohbet edelim mi?"
    ).random()

    fun unreachable(): String = listOf(
        "Şu an yapay zekaya ulaşamadım, internetine bir bakıp biraz sonra tekrar dener misin?",
        "Bir türlü bağlanamadım, ağda bir sorun olabilir. Birazdan tekrar deneyelim mi?",
        "Yapay zeka tarafına ulaşamıyorum şu an. Bağlantını kontrol edip bana tekrar söyler misin?"
    ).random()
}
