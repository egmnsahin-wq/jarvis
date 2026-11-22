# Jarvis Masaüstü (Windows)

Telefondaki Jarvis'in bilgisayar sürümü. Aynı Gemini anahtarıyla çalışır, doğal cümleleri anlar ve telefonla birbirini kontrol eder.

## Kurulum
1. GitHub > Actions > "Build Windows" çalışmasından **JarvisMasaustu-windows** dosyasını indir, zip'ten `JarvisMasaustu.exe` çıkar.
2. Çift tıkla. Windows "bilinmeyen yayıncı" derse **Ek bilgi > Yine de çalıştır**. Güvenlik duvarı sorarsa **Erişime izin ver** (telefon bağlantısı için gerekli).
3. İlk açılışta Ayarlar açılır: ücretsiz Gemini anahtarını yapıştır (adımlar ekranda yazıyor).
- exe açılmazsa: Python 3.11 kur, `windows` klasöründeki `calistir.bat` dosyasına çift tıkla.

## Telefonla bağlama (iki yönlü)
- **Telefon → Bilgisayar** ("bilgisayarda Chrome'u aç", "bilgisayarı kilitle", "bilgisayarın sesini kıs"): telefonda Jarvis Ayarlar > *Bilgisayar IP Adresi* kutusuna bilgisayarın IP'sini yaz (Masaüstü ayarlarında yazar). Bilgisayardaki Ayarlar > Telefonlar bölümüne telefonun IP'sini de ekle (kayıtlı olmayan cihazlar varsayılan olarak engellenir).
- **Bilgisayar → Telefon** ("telefonumu çaldır", "Ahmet'i telefondan ara", "telefonda fener aç"): Masaüstü Ayarlar > Telefonlar'a telefonun IP'sini yaz, telefonda Jarvis Ayarlar > *Uzaktan kontrole aç* açık olsun. "Test" düğmesi bağlantıyı denetler.
- İki cihaz da aynı Wi-Fi'de olmalı.

## Neler yapar
Uygulama aç/kapat, site aç, Google/YouTube arama, şarkı aç, ses (aç/kıs/yüzde kaç), medya tuşları, kilitle/uyku/kapat/yeniden başlat (kapatmadan önce onay sorar, 15 sn içinde iptal edilebilir), ekran görüntüsü, saat/tarih/pil, haberler, not alma/okuma, zamanlayıcı, telefona komut, genel sohbet. "Jarvis" diyerek uyandırma için *Sürekli dinle* kutusunu işaretle.
