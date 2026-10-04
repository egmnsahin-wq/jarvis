package com.jarvis.assistant.ai

import android.content.Context
import com.jarvis.assistant.data.PreferencesManager
import org.json.JSONObject

/** Structured result of asking an AI backend to understand a free-form spoken sentence. */
data class JarvisIntent(
    val intent: String,
    val query: String? = null,
    val note: String? = null,
    val minutes: Int? = null,
    val hour: Int? = null,
    val minute: Int? = null,
    val contact: String? = null,
    val message: String? = null,
    val app: String? = null,
    val device: String? = null,
    val tvApp: String? = null,
    val tvUrl: String? = null,
    val tvVolume: Int? = null,
    val targetPhone: String? = null,
    val song: String? = null,
    val brightnessPercent: Int? = null,
    val aliasName: String? = null,
    val aliasTarget: String? = null,
    val templateName: String? = null,
    val wifiSsid: String? = null,
    val pcApp: String? = null,
    val pcUrl: String? = null,
    val reply: String
)

/**
 * The JSON intent schema, prompt text and response parsing are identical no matter which AI
 * backend answers (Gemini or Groq) — this keeps them in one place so switching providers in
 * Settings never changes what Jarvis is capable of understanding.
 */
object IntentSchema {

    fun buildSystemPrompt(context: Context): String {
        val name = PreferencesManager.getAssistantName(context)
        val personality = PreferencesManager.getPersonality(context)
        val registeredPhones = PreferencesManager.getRemotePhones(context).map { it.name }
        val phoneListLine = if (registeredPhones.isEmpty())
            "Şu an kayıtlı hiçbir uzak telefon yok, o yüzden targetPhone'u her zaman null bırak."
        else
            "Kayıtlı telefon isimleri TAM OLARAK şunlar: ${registeredPhones.joinToString(", ")}. " +
                "targetPhone alanına SADECE bu listedeki ismi harfi harfine aynen yaz (büyük/küçük harf de dahil), " +
                "kendi uydurduğun bir isim yazma. Kullanıcının söylediği isim bu listedekilerden birine " +
                "en yakınsa o listedeki ismi kullan; hiçbirine benzemiyorsa targetPhone'u null bırak."
        val memories = PreferencesManager.getMemories(context)
        val memoryLine = if (memories.isEmpty())
            "Kullanıcı hakkında henüz kalıcı olarak hatırladığın bir şey yok."
        else
            "Kullanıcı hakkında kalıcı olarak hatırladığın bilgiler: ${memories.joinToString(" | ")}. " +
                "Bunları, konuyla alakalıysa cevaplarında doğal şekilde kullan (sormasını beklemeden)."
        return """
            Sen $name adında bir sesli asistansın. $personality
            Kullanıcının söylediği Türkçe cümleyi analiz et ve SADECE aşağıdaki JSON şemasında,
            başka hiçbir açıklama, markdown ya da kod bloğu olmadan, saf JSON olarak cevap ver:

            {"intent":"<TIME|WEATHER|FLASHLIGHT_ON|FLASHLIGHT_OFF|VOLUME_UP|VOLUME_DOWN|SILENT_ON|SILENT_OFF|BRIGHTNESS_UP|BRIGHTNESS_DOWN|BATTERY|SEARCH_GOOGLE|SEARCH_YOUTUBE|PLAY_SONG|NOTE_ADD|NOTES_READ|NOTES_CLEAR|REMINDER|ALARM|TIMER|CALL|SMS|WHATSAPP|OPEN_APP|SMART_HOME_ON|SMART_HOME_OFF|TV_OPEN_APP|TV_OPEN_URL|TV_POWER_OFF|TV_VOLUME_UP|TV_VOLUME_DOWN|TV_SET_VOLUME|TV_MUTE|TV_UNMUTE|REMOTE_PLAY_SONG|REMOTE_SEARCH_GOOGLE|REMOTE_SEARCH_YOUTUBE|REMOTE_SET_ALARM|REMOTE_REMINDER|REMOTE_CALL|REMOTE_SMS|REMOTE_WHATSAPP|REMOTE_OPEN_APP|REMOTE_FLASHLIGHT_ON|REMOTE_FLASHLIGHT_OFF|REMOTE_BATTERY|REMOTE_FIND_PHONE|REMOTE_NOTIFY|MEMORY_SAVE|MEMORY_READ|MEMORY_CLEAR|BRIGHTNESS_SET|WIFI_SETTINGS|BLUETOOTH_SETTINGS|FIND_MY_PHONE|ALARM_LIST|MISSED_CALLS|REMOTE_BATTERY_ALL|TODO_ADD|TODO_DONE|TODO_LIST|TODO_CLEAR_DONE|ALIAS_SET|TEMPLATE_SET|TEMPLATE_SEND|CALCULATE|CURRENCY|NEWS|DAILY_SUMMARY|CONVERSATION_EXPORT|HOME_WIFI_SET|ARRIVAL_REMINDER|PC_OPEN_APP|PC_OPEN_URL|PC_SHUTDOWN|PC_RESTART|PC_SLEEP|PC_LOCK|PC_VOLUME_UP|PC_VOLUME_DOWN|PC_MUTE|PC_MEDIA_PLAY_PAUSE|PC_MEDIA_NEXT|PC_MEDIA_PREV|FILE_ANALYZE|FILE_ANALYZE_TO_FILE|CREATE_FILE|SCREEN_ASK|CHAT>",
             "query":"<arama/şarkı metni varsa, yoksa null>",
             "note":"<not edilecek metin varsa, yoksa null>",
             "minutes":<hatırlatıcı/sayaç için dakika sayısı varsa, yoksa null>,
             "hour":<alarm saati varsa, yoksa null>,
             "minute":<alarm dakikası varsa, yoksa null>,
             "contact":"<aranacak/mesaj atılacak kişi adı varsa, yoksa null>",
             "message":"<sms/whatsapp/hatırlatıcı/bildirim mesaj metni varsa, yoksa null>",
             "app":"<TELEFONDA (bu cihazda) açılacak uygulama adı varsa, yoksa null>",
             "device":"<ev otomasyonu cihaz adı varsa, yoksa null>",
             "tvApp":"<TELEVIZYONDA açılacak uygulama adı varsa (örn. youtube, netflix), yoksa null>",
             "tvUrl":"<televizyonun tarayıcısında açılacak URL varsa, yoksa null>",
             "tvVolume":<televizyon sesi belirli bir seviyeye ayarlanacaksa 0-100 arası sayı, yoksa null>,
             "targetPhone":"<komutun gönderileceği KAYITLI BAŞKA TELEFONUN adı varsa (örn. \"Samsung\"), yoksa null>",
             "song":"<çalınacak şarkı/sanatçı adı varsa, yoksa null>",
             "brightnessPercent":<ekran parlaklığı yüzde olarak belirtildiyse 0-100 arası sayı, yoksa null>,
             "aliasName":"<tanımlanacak takma ad varsa (örn. \"karım\"), yoksa null>",
             "aliasTarget":"<takma adın karşılık geldiği gerçek kişi adı varsa, yoksa null>",
             "templateName":"<hazır cevap şablonunun adı varsa, yoksa null>",
             "wifiSsid":"<ev Wi-Fi ağının adı belirtildiyse, yoksa null>",
             "pcApp":"<BİLGİSAYARDA açılacak uygulama adı varsa, yoksa null>",
             "pcUrl":"<bilgisayarın tarayıcısında açılacak URL varsa, yoksa null>",
             "reply":"<kullanıcıya söyleyeceğin KISA (en fazla 2 cümle), doğal, sesli okunacak Türkçe cevap veya onay — HER ZAMAN doldur>"}

            Kullanıcı bir eylem istemiyorsa (sohbet, soru, bilgi isteği) intent olarak "CHAT" kullan
            ve tüm cevabı "reply" alanına yaz. Örnek: "su içmem lazım hatırlatmayı unutma" ->
            intent REMINDER, minutes tahmini bir süre (belirtilmemişse 30), message "su iç".

            "X aç" dendiğinde EĞER cümlede "televizyonda/tv'de/tv de" gibi bir ifade varsa
            intent TV_OPEN_APP ve tvApp alanını doldur; "bilgisayarda/pc'de/pc de/computer'da"
            gibi bir ifade varsa intent PC_OPEN_APP ve pcApp alanını doldur; yoksa (sadece
            "X aç" ise) bu telefonda açılacağı için intent OPEN_APP ve app alanını doldur.
            "Televizyonu kapat" -> TV_POWER_OFF. "Televizyonun sesini aç/kıs" ->
            TV_VOLUME_UP/TV_VOLUME_DOWN. "Televizyonu sessize al" -> TV_MUTE, "televizyonun
            sesini geri aç" -> TV_UNMUTE.

            Bilgisayar komutları: "bilgisayarı kapat" -> PC_SHUTDOWN, "yeniden başlat" ->
            PC_RESTART, "uyku moduna al" -> PC_SLEEP, "kilitle" -> PC_LOCK, "bilgisayarın sesini
            aç/kıs" -> PC_VOLUME_UP/PC_VOLUME_DOWN, "sessize al" -> PC_MUTE, "oynat/duraklat" ->
            PC_MEDIA_PLAY_PAUSE, "sonraki/önceki parça" -> PC_MEDIA_NEXT/PC_MEDIA_PREV,
            "bilgisayarda [site] aç" -> PC_OPEN_URL, pcUrl alanına yaz.

            Dosya ve ekran komutları:
            - "bu dosyayı analiz et", "dosyada ne yazıyor", "bu dosya ne işe yarıyor" (kullanıcı
              az önce bir dosya seçtiyse) -> FILE_ANALYZE, query alanına varsa özel bir soru yaz
              (yoksa null, genel bir özet yapılır).
            - "bu dosyayı analiz edip başka bir dosyaya yaz" -> FILE_ANALYZE_TO_FILE.
            - "[konu] hakkında bir dosya oluştur", "şunu bir dosyaya yaz: [içerik]" -> CREATE_FILE,
              note alanına ne yazılacağını/oluşturulacağını yaz.
            - "ekranımda ne var", "bu ekranda ne yazıyor", "şunu ekrana bakarak cevapla: [soru]"
              -> SCREEN_ASK, query alanına soru varsa yaz (yoksa null, genel açıklama yapılır).

            ÖNEMLİ — OPEN_APP sadece kullanıcı SADECE BİR UYGULAMA ADI söylediğinde kullanılır
            ("youtube aç", "instagram'ı aç", "spotify'ı başlat" gibi — başka hiçbir içerik/şarkı/
            arama belirtilmemiş). Eğer cümlede bir şarkı, sanatçı ya da içerik belirtiliyorsa
            ("youtube'dan bir şarkı aç", "spotify'da tarkan çal", "youtube'da lofi müzik aç",
            "bana bir şarkı açar mısın") bu ASLA OPEN_APP değildir:
            - Kullanıcı açıkça "ara"/"arat" dediyse (gerçek bir arama sonucu listesi istiyorsa,
              örn. "youtube'da tarkan ara") -> intent SEARCH_YOUTUBE, query alanına arama
              terimini yaz.
            - Kullanıcı "aç/çal/dinlet/başlat" gibi bir fiil kullandıysa (yani doğrudan çalmasını
              istiyorsa — hangi uygulamadan olduğu fark etmez, "youtube'dan" dese bile) -> intent
              PLAY_SONG, song alanına şarkı/sanatçı/içerik adını yaz (belirtilmemişse makul bir
              öneri yaz, asla boş bırakma). PLAY_SONG artık gerçekten o şarkıyı bulup çalar,
              sadece uygulamayı açmaz.

            Cümlede kayıtlı bir telefon adı geçiyorsa (örn. "Samsung telefonda X şarkısını aç",
            "[isim] telefondan babamı ara", "[isim] telefonda alarm kur") bunu targetPhone
            alanına yaz ve karşılık gelen REMOTE_ intent'ini kullan: REMOTE_PLAY_SONG (song),
            REMOTE_SEARCH_GOOGLE/REMOTE_SEARCH_YOUTUBE (query), REMOTE_SET_ALARM (hour, minute),
            REMOTE_REMINDER (message, minutes), REMOTE_CALL/REMOTE_SMS/REMOTE_WHATSAPP (contact,
            message), REMOTE_OPEN_APP (app), REMOTE_FLASHLIGHT_ON/OFF, REMOTE_BATTERY,
            REMOTE_FIND_PHONE (telefonu bulmak için çaldır), REMOTE_NOTIFY (message, o telefonun
            ekranında bildirim göster). targetPhone doluysa asla normal (uzak olmayan) intent
            kullanma, mutlaka REMOTE_ karşılığını seç.

            $phoneListLine

            $memoryLine

            Kullanıcı senden bir şeyi KALICI olarak hatırlamanı istediğinde ("bunu hatırla",
            "şunu unutma", "aklında tutar mısın", "benim ... olduğumu bil" gibi) intent
            MEMORY_SAVE, note alanına hatırlanacak bilgiyi kısa ve net şekilde yaz (örn. "kedisinin
            adı Boncuk", "her sabah 7'de koşuya çıkıyor"). "Hakkımda ne biliyorsun", "neyi
            hatırlıyorsun" gibi sorularda MEMORY_READ. "Hafızanı temizle", "beni unut", "hatırladığın
            her şeyi sil" gibi isteklerde MEMORY_CLEAR.

            Diğer yeni komutlar:
            - "ekran parlaklığını yüzde 50 yap" -> BRIGHTNESS_SET, brightnessPercent 50
            - "wifi ayarlarını aç" -> WIFI_SETTINGS. "bluetooth'u aç" -> BLUETOOTH_SETTINGS
              (ikisi de sadece ilgili ayar ekranını açar, kullanıcı son dokunuşu kendi yapar —
              bunu reply'da doğal şekilde belirt)
            - "telefonumu bul" / "telefonum nerede" (KAYITLI BAŞKA telefon adı GEÇMİYORSA, yani
              bu cihazın kendisi için) -> FIND_MY_PHONE. Bir telefon adı geçiyorsa onun yerine
              REMOTE_FIND_PHONE kullan.
            - "alarmlarımı göster/listele" -> ALARM_LIST
            - "kim aramış", "cevapsız arama var mı" -> MISSED_CALLS
            - "tüm telefonların pilini söyle", "diğer telefonların durumu ne" -> REMOTE_BATTERY_ALL
            - Yapılacaklar listesi: "yapılacaklara ekle: [iş]" / "[iş] yapmam lazım not al" ->
              TODO_ADD, note alanına işi yaz. "[iş] yaptım/tamamladım/bitirdi" -> TODO_DONE, note
              alanına hangi işin tamamlandığını yaz. "yapılacaklarımı oku/göster" -> TODO_LIST.
              "tamamlananları temizle" -> TODO_CLEAR_DONE.
            - Kişi takma adı: "[isim]'e [takma ad] de", "[takma ad] dediğimde [isim]'i ara" gibi
              isteklerde ALIAS_SET, aliasName alanına takma adı, aliasTarget alanına gerçek kişi
              adını yaz (örn. "ayşeye karım de" -> aliasName "karım", aliasTarget "ayşe").
            - Hazır cevap şablonu: "[şablon adı] şablonunu şöyle ayarla: [metin]" -> TEMPLATE_SET,
              templateName ve message alanlarını doldur. "[kişiye] [şablon adı] şablonunu gönder"
              -> TEMPLATE_SEND, contact ve templateName alanlarını doldur.
            - Basit hesaplama ("125 çarpı 8 kaç eder", "yüzde 18 kdv 250 lira ne eder" gibi) ->
              CALCULATE, query alanına HESAPLANACAK SAF matematiksel ifadeyi yaz (örn. "125*8",
              "250*1.18"), kelimeleri sayı/işleme çevir.
            - Döviz/kripto kuru ("dolar kaç tl", "bitcoin fiyatı ne kadar") -> CURRENCY, query
              alanına tam cümleyi yaz.
            - Haberler ("gündemde ne var", "spor haberleri neler") -> NEWS, query alanına konu
              varsa yaz (yoksa null, genel gündem gösterilir).
            - "bugün ne var", "günüm nasıl", "ajandamı özetle" -> DAILY_SUMMARY.
            - "konuşmalarımızı dışa aktar/kaydet/paylaş" -> CONVERSATION_EXPORT.
            - "ev wifi ağımı [isim] olarak ayarla" (eve varınca hatırlatıcıların tetiklenmesi
              için) -> HOME_WIFI_SET, wifiSsid alanını doldur.
            - "eve varınca [iş] hatırlat", "eve gelince beni uyar: [iş]" -> ARRIVAL_REMINDER,
              message alanına hatırlatılacak işi yaz (ev Wi-Fi'sı önceden HOME_WIFI_SET ile
              ayarlanmış olmalı, kullanıcı ayarlamadıysa reply'da bunu hatırlat).

            ÇOK ÖNEMLİ — doğal dil anlama: kullanıcı komutları hiçbir zaman kalıplaşmış, sabit bir
            şablonla söylemek zorunda değil. "[isim]'e mesaj gönder: [metin]" gibi katı bir kalıp
            BEKLEME — insanların gerçekte konuştuğu gibi, son derece serbest ve gündelik cümleleri
            de doğru anlaman gerekiyor. Örnekler:
            - "ahmete bugün işim var yazar mısın" -> intent WHATSAPP, contact "ahmet",
              message "Bugün işim var"
            - "anneme az sonra geliyorum de" -> intent WHATSAPP, contact "annem",
              message "Az sonra geliyorum"
            - "babamı arar mısın" / "babamla görüşmem lazım onu ara" -> intent CALL, contact "babam"
            - "ayşeye sms at, toplantı 3'e kaydı diye" -> intent SMS, contact "ayşe",
              message "Toplantı 3'e kaydı"
            Kullanıcı açıkça "sms" veya "mesaj (telefon numarasıyla)" demedikçe, bir kişiye yazma/
            söyleme isteklerini varsayılan olarak WHATSAPP kabul et — Türkiye'de gündelik "yaz",
            "söyle", "ilet" gibi ifadeler günlük hayatta genelde WhatsApp'ı işaret eder.
            Bu esneklik SADECE mesajlaşma için değil, TÜM komutlar için geçerli: uygulama açma,
            televizyon/uzak telefon komutları, ev otomasyonu vb. her şeyde kullanıcının kelimesi
            kelimesine söylediği kalıba değil, NE DEMEK İSTEDİĞİNE odaklan.

            "reply" alanı için: aynı durumda bile HER SEFERİNDE birebir aynı cümleyi kurma, doğal
            konuşan bir asistan gibi biraz farklı ama kısa ve samimi ifadeler kullan (örn. bazen
            "Tamam, arıyorum.", bazen "Hemen bağlanıyorum.", bazen "Oldu, çeviriyorum." gibi).

            Emin olmadığın alanları null bırak, asla uydurma bilgi verme. ASLA cevabının tamamını
            (bütün JSON'u) json null yapma veya "null" kelimesini tek başına bir alan değeri olarak
            yazma — bir şeyden emin değilsen o alanı gerçek JSON null yap, "null" kelimesini metin
            olarak YAZMA. Cevabın her zaman geçerli, eksiksiz bir JSON nesnesi olmalı.
        """.trimIndent()
    }

    fun parseIntentJson(raw: String): JarvisIntent {
        val cleaned = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

        // Guards against the "chat null" bug: the model sometimes returns the bare JSON literal
        // `null`, or an empty body, instead of a proper intent object — never echo that as speech.
        if (cleaned.isBlank() || cleaned.equals("null", ignoreCase = true)) {
            return JarvisIntent(intent = "CHAT", reply = "Tam anlayamadım, bir daha söyler misin?")
        }

        return try {
            val obj = JSONObject(cleaned)
            fun str(key: String): String? {
                if (!obj.has(key) || obj.isNull(key)) return null
                val v = obj.optString(key)
                // Some models write the STRING "null" instead of a real JSON null — treat it the same.
                return v.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
            }
            fun int(key: String): Int? = if (obj.has(key) && !obj.isNull(key)) obj.optInt(key) else null

            val reply = obj.optString("reply", "Tamam.").let {
                if (it.isBlank() || it.equals("null", ignoreCase = true)) "Tamam." else it
            }

            JarvisIntent(
                intent = obj.optString("intent", "CHAT").ifBlank { "CHAT" },
                query = str("query"),
                note = str("note"),
                minutes = int("minutes"),
                hour = int("hour"),
                minute = int("minute"),
                contact = str("contact"),
                message = str("message"),
                app = str("app"),
                device = str("device"),
                tvApp = str("tvApp"),
                tvUrl = str("tvUrl"),
                tvVolume = int("tvVolume"),
                targetPhone = str("targetPhone"),
                song = str("song"),
                brightnessPercent = int("brightnessPercent"),
                aliasName = str("aliasName"),
                aliasTarget = str("aliasTarget"),
                templateName = str("templateName"),
                wifiSsid = str("wifiSsid"),
                pcApp = str("pcApp"),
                pcUrl = str("pcUrl"),
                reply = reply
            )
        } catch (e: Exception) {
            JarvisIntent(intent = "CHAT", reply = "Tam anlayamadım, bir daha söyler misin?")
        }
    }

    /** Distinctive marker text so [isRateLimited] can recognize a 429 regardless of which provider hit it. */
    private const val RATE_LIMIT_MARKER = "ücretsiz kullanım limitine takıldık"

    fun friendlyErrorMessage(code: Int, providerName: String): String = when (code) {
        -1 -> "İnternet bağlantısı yok gibi görünüyor, bağlantını kontrol eder misin?"
        429 -> "$providerName için şu an $RATE_LIMIT_MARKER. Birkaç dakika sonra tekrar dener misin?"
        401, 403 -> "$providerName API anahtarı geçersiz görünüyor. Ayarlar sekmesinden kontrol et."
        404 -> "$providerName için kullanılan model artık geçerli değil, güncellenmesi gerekiyor."
        in 500..599 -> "$providerName sunucularında geçici bir sorun var, biraz sonra tekrar dener misin?"
        else -> "$providerName isteği başarısız oldu ($code)."
    }

    fun isRateLimited(intent: JarvisIntent): Boolean =
        intent.intent == "CHAT" && intent.reply.contains(RATE_LIMIT_MARKER)
}
