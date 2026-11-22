"""Windows eylemleri. Her fonksiyon kısa bir Türkçe sonuç metni döndürür."""
import ctypes
import datetime
import glob
import os
import re
import subprocess
import sys
import urllib.parse
import urllib.request
import webbrowser
import xml.etree.ElementTree as ET

import config

IS_WIN = sys.platform == "win32"
NO_WINDOW = 0x08000000 if IS_WIN else 0

VK = {"VOLUME_MUTE": 0xAD, "VOLUME_DOWN": 0xAE, "VOLUME_UP": 0xAF,
      "MEDIA_NEXT": 0xB0, "MEDIA_PREV": 0xB1, "MEDIA_PLAY_PAUSE": 0xB3}

APP_ALIASES = {
    "chrome": "chrome", "google chrome": "chrome", "krom": "chrome",
    "edge": "msedge", "microsoft edge": "msedge", "tarayıcı": "msedge",
    "firefox": "firefox", "spotify": "spotify", "discord": "discord", "steam": "steam", "telegram": "telegram",
    "whatsapp": "whatsapp", "notepad": "notepad", "not defteri": "notepad", "not defterini": "notepad",
    "hesap makinesi": "calc", "hesap makinesini": "calc", "calculator": "calc",
    "paint": "mspaint", "dosya gezgini": "explorer", "gezgin": "explorer", "explorer": "explorer",
    "görev yöneticisi": "taskmgr", "komut istemi": "cmd", "cmd": "cmd", "powershell": "powershell",
    "word": "winword", "excel": "excel", "powerpoint": "powerpnt", "ayarlar": "ms-settings:",
    "vscode": "code", "visual studio code": "code", "ekran alıntısı": "snippingtool",
}
PROCESS_NAMES = {"chrome": "chrome.exe", "msedge": "msedge.exe", "firefox": "firefox.exe", "spotify": "spotify.exe",
                 "discord": "discord.exe", "steam": "steam.exe", "telegram": "telegram.exe", "notepad": "notepad.exe",
                 "calc": "CalculatorApp.exe", "mspaint": "mspaint.exe", "winword": "winword.exe",
                 "excel": "excel.exe", "powerpnt": "powerpnt.exe", "code": "code.exe", "whatsapp": "whatsapp.exe"}


def tr_lower(s):
    return (s or "").replace("İ", "i").replace("I", "ı").lower()


def _need_windows():
    return None if IS_WIN else "Bu komut sadece Windows'ta çalışır."


# --------------------------------------------------------------- uygulama / web
def _start_menu_lnks():
    roots = [os.path.join(os.environ.get("ProgramData", ""), r"Microsoft\Windows\Start Menu\Programs"),
             os.path.join(os.environ.get("APPDATA", ""), r"Microsoft\Windows\Start Menu\Programs")]
    out = []
    for r in roots:
        if r and os.path.isdir(r):
            out += glob.glob(os.path.join(r, "**", "*.lnk"), recursive=True)
    return out


def open_app(name):
    err = _need_windows()
    if err:
        return err
    raw = (name or "").strip()
    if not raw:
        return "Hangi uygulamayı açayım?"
    key = tr_lower(raw)
    target = APP_ALIASES.get(key, key)
    try:
        os.startfile(target)  # type: ignore[attr-defined]
        return f"{raw} açılıyor."
    except Exception:
        pass
    try:
        subprocess.Popen(["cmd", "/c", "start", "", target], creationflags=NO_WINDOW)
        # start başarısız olsa da hata kodu vermez; kısayol aramasını yine de dene
    except Exception:
        pass
    # Başlat menüsünde bulanık eşleşme
    words = [w for w in re.split(r"\s+", key) if w]
    best = None
    for lnk in _start_menu_lnks():
        base = tr_lower(os.path.splitext(os.path.basename(lnk))[0])
        if key == base or all(w in base for w in words):
            if best is None or len(base) < len(tr_lower(os.path.splitext(os.path.basename(best))[0])):
                best = lnk
    if best:
        try:
            os.startfile(best)  # type: ignore[attr-defined]
            return f"{raw} açılıyor."
        except Exception:
            pass
    return f"\"{raw}\" adlı bir uygulama bulamadım."


def close_app(name):
    err = _need_windows()
    if err:
        return err
    key = tr_lower(name)
    exe = PROCESS_NAMES.get(APP_ALIASES.get(key, key))
    if not exe:
        exe = (APP_ALIASES.get(key, key).replace(" ", "")) + ".exe"
    r = subprocess.run(["taskkill", "/im", exe, "/f"], capture_output=True, creationflags=NO_WINDOW)
    return f"{name} kapatıldı." if r.returncode == 0 else f"{name} çalışıyor görünmüyor."


def open_url(url):
    url = (url or "").strip()
    if not url:
        return "Hangi adresi açayım?"
    if not re.match(r"^[a-z]+://", url):
        url = "https://" + url
    webbrowser.open(url)
    return "Açıyorum."


def search_google(query):
    webbrowser.open("https://www.google.com/search?q=" + urllib.parse.quote(query or ""))
    return f"Google'da \"{query}\" için arıyorum."


def search_youtube(query):
    webbrowser.open("https://www.youtube.com/results?search_query=" + urllib.parse.quote(query or ""))
    return f"YouTube'da \"{query}\" için arıyorum."


def play_song(query):
    webbrowser.open("https://www.youtube.com/results?search_query=" + urllib.parse.quote(query or ""))
    return f"{query} için YouTube'u açtım."


# --------------------------------------------------------------- ses / medya
def _key(vk, times=1):
    if not IS_WIN:
        return
    u = ctypes.windll.user32
    for _ in range(times):
        u.keybd_event(vk, 0, 0, 0)
        u.keybd_event(vk, 0, 2, 0)


def volume_up():
    err = _need_windows()
    if err:
        return err
    _key(VK["VOLUME_UP"], 5)
    return "Sesi artırdım."


def volume_down():
    err = _need_windows()
    if err:
        return err
    _key(VK["VOLUME_DOWN"], 5)
    return "Sesi kıstım."


def volume_set(level):
    err = _need_windows()
    if err:
        return err
    try:
        level = max(0, min(100, int(level)))
    except Exception:
        return "Ses seviyesini anlayamadım."
    _key(VK["VOLUME_DOWN"], 50)           # her basış %2
    _key(VK["VOLUME_UP"], round(level / 2))
    return f"Sesi yüzde {level} yaptım."


def mute():
    err = _need_windows()
    if err:
        return err
    _key(VK["VOLUME_MUTE"])
    return "Sesi kapattım."


def media(kind):
    err = _need_windows()
    if err:
        return err
    _key(VK[kind])
    return {"MEDIA_PLAY_PAUSE": "Oynat/duraklat yapıldı.", "MEDIA_NEXT": "Sonraki parça.", "MEDIA_PREV": "Önceki parça."}[kind]


# --------------------------------------------------------------- güç / oturum
def lock():
    err = _need_windows()
    if err:
        return err
    ctypes.windll.user32.LockWorkStation()
    return "Bilgisayar kilitlendi."


def sleep():
    err = _need_windows()
    if err:
        return err
    subprocess.Popen(["rundll32.exe", "powrprof.dll,SetSuspendState", "0,1,0"], creationflags=NO_WINDOW)
    return "Uyku moduna alıyorum."


def shutdown():
    err = _need_windows()
    if err:
        return err
    subprocess.Popen(["shutdown", "/s", "/t", "15"], creationflags=NO_WINDOW)
    return "Bilgisayar 15 saniye içinde kapanacak. Vazgeçersen \"kapatmayı iptal et\" de."


def restart():
    err = _need_windows()
    if err:
        return err
    subprocess.Popen(["shutdown", "/r", "/t", "15"], creationflags=NO_WINDOW)
    return "Bilgisayar 15 saniye içinde yeniden başlayacak. Vazgeçersen \"iptal et\" de."


def cancel_shutdown():
    err = _need_windows()
    if err:
        return err
    r = subprocess.run(["shutdown", "/a"], capture_output=True, creationflags=NO_WINDOW)
    return "Kapatmayı iptal ettim." if r.returncode == 0 else "İptal edilecek bir kapatma yok."


def minimize_all():
    err = _need_windows()
    if err:
        return err
    u = ctypes.windll.user32
    u.keybd_event(0x5B, 0, 0, 0)
    u.keybd_event(0x44, 0, 0, 0)
    u.keybd_event(0x44, 0, 2, 0)
    u.keybd_event(0x5B, 0, 2, 0)
    return "Masaüstünü gösterdim."


def screenshot():
    err = _need_windows()
    if err:
        return err
    pics = os.path.join(os.path.expanduser("~"), "Pictures")
    os.makedirs(pics, exist_ok=True)
    path = os.path.join(pics, datetime.datetime.now().strftime("ekran_%Y%m%d_%H%M%S.png"))
    ps = ("Add-Type -AssemblyName System.Windows.Forms,System.Drawing;"
          "$b=[System.Windows.Forms.SystemInformation]::VirtualScreen;"
          "$bmp=New-Object System.Drawing.Bitmap $b.Width,$b.Height;"
          "$g=[System.Drawing.Graphics]::FromImage($bmp);"
          "$g.CopyFromScreen($b.Location,[System.Drawing.Point]::Empty,$b.Size);"
          "$bmp.Save($env:JARVIS_PATH);")
    r = subprocess.run(["powershell", "-NoProfile", "-Command", ps], capture_output=True,
                       env={**os.environ, "JARVIS_PATH": path}, creationflags=NO_WINDOW)
    return f"Ekran görüntüsünü Resimler klasörüne kaydettim." if r.returncode == 0 else "Ekran görüntüsü alınamadı."


# --------------------------------------------------------------- bilgi
def time_now():
    n = datetime.datetime.now()
    return f"Saat {n.hour}:{n.minute:02d}."


def date_today():
    n = datetime.datetime.now()
    aylar = ["ocak", "şubat", "mart", "nisan", "mayıs", "haziran", "temmuz", "ağustos", "eylül", "ekim", "kasım", "aralık"]
    gunler = ["pazartesi", "salı", "çarşamba", "perşembe", "cuma", "cumartesi", "pazar"]
    return f"Bugün {n.day} {aylar[n.month - 1]} {n.year}, {gunler[n.weekday()]}."


def battery():
    if not IS_WIN:
        return "Bu komut sadece Windows'ta çalışır."

    class SPS(ctypes.Structure):
        _fields_ = [("AC", ctypes.c_ubyte), ("Flag", ctypes.c_ubyte), ("Percent", ctypes.c_ubyte),
                    ("Saver", ctypes.c_ubyte), ("Life", ctypes.c_ulong), ("Full", ctypes.c_ulong)]
    s = SPS()
    ctypes.windll.kernel32.GetSystemPowerStatus(ctypes.byref(s))
    if s.Flag == 128 or s.Percent == 255:
        return "Bu bilgisayarda pil yok, masaüstü olabilir."
    return f"Pil yüzde {s.Percent}" + (", şarjda." if s.AC == 1 else ".")


def news(query=None):
    url = ("https://news.google.com/rss/search?q=" + urllib.parse.quote(query) + "&hl=tr&gl=TR&ceid=TR:tr"
           if query else "https://news.google.com/rss?hl=tr&gl=TR&ceid=TR:tr")
    try:
        with urllib.request.urlopen(url, timeout=12) as r:
            root = ET.fromstring(r.read())
        titles = []
        for item in root.iter("item"):
            t = (item.findtext("title") or "").strip()
            t = re.sub(r"\s+-\s+[^-]+$", "", t)
            if t:
                titles.append(t)
            if len(titles) == 5:
                break
        if not titles:
            return "Haber bulamadım."
        return "Başlıklar: " + ". ".join(f"{i + 1}. {t}" for i, t in enumerate(titles))
    except Exception:
        return "Haberlere şu an ulaşamadım."


def note_add(text):
    if not (text or "").strip():
        return "Ne not alayım?"
    with open(config.NOTES_PATH, "a", encoding="utf-8") as f:
        f.write(f"[{datetime.datetime.now():%d.%m.%Y %H:%M}] {text.strip()}\n")
    return "Notu kaydettim."


def notes_read():
    try:
        with open(config.NOTES_PATH, encoding="utf-8") as f:
            lines = [l.strip() for l in f if l.strip()][-5:]
        return "Son notların: " + " ".join(lines) if lines else "Hiç notun yok."
    except FileNotFoundError:
        return "Hiç notun yok."
