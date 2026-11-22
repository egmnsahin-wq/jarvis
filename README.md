# Jarvis — Kişisel Sesli Asistan (Android)

Tablet ve telefonda çalışan, tamamen ücretsiz araçlarla yapılmış Jarvis tarzı sesli asistan.

## Özellikler
- 🎙️ **Push-to-talk** ses girişi (Android'in yerleşik SpeechRecognizer'ı — ücretsiz)
- 🔊 **Doğal sesli çıkış** (cihazın Google TTS motoru, en yüksek kaliteli Türkçe ses otomatik seçilir)
- 🧠 **Akıllı sohbet / soru-cevap** (Gemini API — ücretsiz kota)
- ☀️ **Hava durumu** (Open-Meteo — key gerektirmez, tamamen ücretsiz)
- ⏰ **Saat, alarm, sayaç** (cihazın kendi Saat uygulamasına entegre)
- 📞 **Telefon kontrolü**: rehberden arama, SMS gönderme, uygulama açma
- 🏠 **Ev otomasyonu altyapısı**: henüz bir platform seçilmedi, webhook tabanlı genel bir yapı hazır (Home Assistant / Tuya / Google Home ile kolayca doldurulabilir)
- 🎨 Koyu tema, mavi/turuncu vurgulu Iron-Man esintili arayüz, animasyonlu ses halkası

## Kurulum

### 1. Projeyi Android Studio'da aç
`File > Open` ile bu klasörü (`JarvisAssistant`) seç. Gradle sync otomatik başlayacaktır (ilk açılışta internet gerekir, Gradle dosyalarını indirir).

### 2. Gemini API key ekle
1. https://aistudio.google.com/app/apikey adresinden ücretsiz bir API key al.
2. Proje kök dizinindeki `local.properties.example` dosyasını `local.properties` olarak kopyala (aynı klasöre).
3. İçindeki `GEMINI_API_KEY=` satırına kendi key'ini yapıştır.
4. `sdk.dir=` satırını kendi Android SDK yoluna göre düzenle (Android Studio genelde bunu otomatik ekler, elle eklemen gerekmeyebilir).

> `local.properties` git'e eklenmez / paylaşılmaz — key'in güvende kalır.

### 3. Çalıştır
Bir tablet/telefon bağla (veya emülatör aç) ve ▶️ Run'a bas. İlk açılışta mikrofon, telefon, SMS, rehber, konum ve bildirim izinleri istenecek — Jarvis'in ilgili özellikleri çalışması için hepsini onayla.

## Kod Yapısı
```
app/src/main/java/com/jarvis/assistant/
├── MainActivity.kt              # Giriş noktası, izin yönetimi
├── ui/
│   ├── theme/                   # Renkler, tipografi, Compose teması
│   ├── components/VoiceRing.kt  # Animasyonlu ses halkası
│   └── screens/HomeScreen.kt    # Ana ekran
├── data/JarvisViewModel.kt      # Durum yönetimi, akışı bağlar
├── speech/
│   ├── SpeechRecognizerManager.kt  # Ses -> metin (STT)
│   └── TextToSpeechManager.kt      # Metin -> ses (TTS)
├── ai/GeminiClient.kt           # Açık uçlu sohbet için Gemini API
└── commands/
    ├── CommandProcessor.kt      # Gelen metni doğru komuta yönlendirir
    ├── WeatherService.kt        # Open-Meteo hava durumu
    ├── PhoneController.kt       # Arama / SMS / uygulama açma
    ├── AlarmController.kt       # Alarm / sayaç (Saat uygulamasına devrediyor)
    └── SmartHomeController.kt   # Ev otomasyonu altyapısı (webhook tabanlı)
```

## Nasıl Genişletilir?

### Yeni sesli komut eklemek
`CommandProcessor.process()` içine yeni bir `text.contains("...")` bloğu eklemen yeterli. Sırası önemli: en spesifik komutlar Gemini fallback'inden önce gelmeli.

### Ev otomasyonunu bağlamak
Bir platform seçtiğinde (`Home Assistant`, `Tuya`, `Google Home`/IFTTT vb.), `SmartHomeController.registerDevice(...)` ile cihazları webhook URL'leriyle kaydet. Örnek:
```kotlin
SmartHomeController.registerDevice(
    SmartHomeController.Device(
        name = "salon lambası",
        onUrl = "http://homeassistant.local:8123/api/webhook/salon_on",
        offUrl = "http://homeassistant.local:8123/api/webhook/salon_off"
    )
)
```
Home Assistant seçersen, doğrudan REST API + access token kullanan özel bir istemciye geçmek daha güçlü olur — istersen bir sonraki adımda onu da ekleyebiliriz.

### Daha da doğal ses (opsiyonel, ileride)
Şu an cihazın yerleşik Google TTS'i kullanılıyor (tamamen ücretsiz, sınırsız). Daha da insansı ses istersen Google Cloud Text-to-Speech'in WaveNet/Neural2 sesleri var ama aylık ücretsiz kotası sınırlı (ilk ~1 milyon karakter/ay ücretsiz) — `TextToSpeechManager`'ı Cloud TTS REST çağrısıyla değiştirerek eklenebilir.

## Bilinen Sınırlamalar / Notlar
- Push-to-talk kullanıldığı için sürekli dinleme ("Jarvis" diyerek uyandırma) yok — istersen sonra `RECORD_AUDIO` + arka plan servisiyle wake-word desteği ekleyebiliriz.
- SMS gönderimi Android 6+'da runtime izin ister; bazı cihazlarda varsayılan SMS uygulaması olmayan cihazlarda kısıtlı çalışabilir.
- Konum, hava durumu için `getLastKnownLocation` kullanıyor (basit/pilsiz); daha hassas konum için `FusedLocationProviderClient` eklenebilir.
- Ev otomasyonu şu an "boş" — cihaz eklemeden komut verirsen Jarvis bunu söyler.

## Haber paneli (el hareketi + dokunma + ses)
- Ana ekranda sol üstteki gazete simgesi ya da "haberleri göster" ile açılır.
- **El hareketi** (panelde el simgesi → kamera izni): açık el sağdan sola = sonraki, soldan sağa = önceki,
  işaret parmağını kameraya yaklaştır = detay, geri çek = listeye dön. Ön/arka kamera düğmesi vardır.
- **Dokunma**: kartları kaydır, karta dokun = detay, arama kutusu ve konu çipleri.
- **Ses**: "sonraki", "önceki", "detay", "3. haber", "oku", "ekonomi haberlerini ara", "kapat".
- El modeli ilk kullanımda internetten indirilir (veya `app/src/main/assets/hand_landmarker.task` olarak koyabilirsin).

## v24 değişiklikleri
- **WhatsApp mesaj asistanı** (Ayarlar'dan aç + Bildirim erişimi izni): gelen mesajda "okumamı ister misin?" → okur → "cevap göndermek ister misin?" → söylediğini bildirimdeki Yanıtla ile yollar.
- **Ücretsiz limit**: tüm sağlayıcıların limiti dolarsa "ücretsiz kullanım limitime takıldım, sonra tekrar konuşmak isterim" der.
- **Konuşma geçmişi** diske kaydedilir (300 mesaj), yapay zekaya son 60 mesaj/14 bin karakter gider.
- Mistral/Cerebras yerine **GitHub Models** ve **NVIDIA NIM** eklendi.
- Eşleştirme: HTTP engeli (cleartext) kaldırıldı, IP gösterimi ve "Bağlantıyı test et" eklendi.
- Mikrofon: ses tanıma servisi görünürlüğü, izin kontrolleri ve hata döngüsü düzeltildi.

## v25 değişiklikleri
- **Tek Gemini anahtarı yeter**: Ayarlar'da adım adım "Anahtar al" rehberi ve "Anahtarı test et" düğmesi var; diğer sağlayıcılar "Yedek" bölümüne taşındı (kapalı gelir).
- **Gemini model adı otomatik**: Google eski modelleri kapattığı için model sabit değil; çalışanı bulur, limit/404'te sıradakine geçer, gerekirse Google'ın model listesinden seçer.
- **Yapay zeka önce**: her cümle önce yapay zekaya gider (doğal dil); internet/anahtar yoksa kural tabanlı komutlar yedek çalışır.
- **Doğal ve değişken cevaplar**; hata olunca yapay zeka hatayı kendi cümleleriyle sade anlatır. "Durumunu kontrol et" diyince kendi kendini tarar.
- **WhatsApp'ta "X'e şunu yaz"**: Erişilebilirlik izni açıksa Gönder tuşuna kendisi basar.
