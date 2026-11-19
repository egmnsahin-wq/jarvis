package com.jarvis.assistant.gesture

/** Kameranın algıladığı el hareketleri. */
enum class HandGesture {
    /** Açık el, sağdan sola kaydırma -> sonraki haber */
    NEXT,
    /** Açık el, soldan sağa kaydırma -> önceki haber */
    PREVIOUS,
    /** İşaret parmağı kameraya yaklaşıyor -> haber detayı */
    OPEN_DETAIL,
    /** İşaret parmağı kameradan uzaklaşıyor -> listeye dön */
    CLOSE_DETAIL
}
