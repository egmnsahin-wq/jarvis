"""Konuşma: ses tanıma (Google, ücretsiz) ve seslendirme (edge-tts; olmazsa Windows SAPI)."""
import asyncio
import ctypes
import os
import subprocess
import sys
import tempfile
import threading
import time

import actions
import config

IS_WIN = sys.platform == "win32"
NO_WINDOW = 0x08000000 if IS_WIN else 0


# ------------------------------------------------------------------ seslendirme
class Speaker:
    def __init__(self):
        self._lock = threading.Lock()

    def say(self, text):
        text = (text or "").strip()
        if not text or not config.cfg.get("speak", True):
            return
        with self._lock:
            if self._edge(text):
                return
            self._sapi(text)

    def _edge(self, text):
        try:
            import edge_tts
            path = os.path.join(tempfile.gettempdir(), "jarvis_tts.mp3")
            asyncio.run(edge_tts.Communicate(text, config.cfg.get("voice", "tr-TR-EmelNeural")).save(path))
            if not IS_WIN:
                return False
            mci = ctypes.windll.winmm.mciSendStringW
            mci("close jarvis", None, 0, 0)
            mci(f'open "{path}" type mpegvideo alias jarvis', None, 0, 0)
            mci("play jarvis wait", None, 0, 0)
            mci("close jarvis", None, 0, 0)
            return True
        except Exception:
            return False

    def _sapi(self, text):
        if not IS_WIN:
            return
        try:
            ps = ("Add-Type -AssemblyName System.Speech;"
                  "$s=New-Object System.Speech.Synthesis.SpeechSynthesizer;$s.Speak($env:JARVIS_TEXT)")
            subprocess.run(["powershell", "-NoProfile", "-Command", ps], env={**os.environ, "JARVIS_TEXT": text},
                           creationflags=NO_WINDOW, timeout=120)
        except Exception:
            pass


# ------------------------------------------------------------------ ses tanıma
def listen_once(timeout=6, phrase_limit=12):
    """Bir cümle dinler. (metin, hata_mesajı) döndürür."""
    try:
        import speech_recognition as sr
    except Exception:
        return None, "Ses tanıma bileşeni yüklenemedi."
    r = sr.Recognizer()
    try:
        with sr.Microphone() as src:
            r.adjust_for_ambient_noise(src, duration=0.4)
            audio = r.listen(src, timeout=timeout, phrase_time_limit=phrase_limit)
    except sr.WaitTimeoutError:
        return None, "Bir şey duyamadım."
    except Exception:
        return None, "Mikrofonu açamadım. Windows Ayarlar > Gizlilik > Mikrofon bölümünden masaüstü uygulamalarına izin ver."
    try:
        return r.recognize_google(audio, language="tr-TR"), None
    except sr.UnknownValueError:
        return None, "Ne dediğini anlayamadım."
    except Exception:
        return None, "Ses tanıma servisine ulaşamadım, internetini kontrol et."


class WakeLoop(threading.Thread):
    """Arka planda sürekli dinler; "Jarvis ..." deyince komutu iletir."""

    def __init__(self, is_busy, on_command, on_wake, on_error):
        super().__init__(daemon=True)
        self.is_busy, self.on_command, self.on_wake, self.on_error = is_busy, on_command, on_wake, on_error
        self._halt = threading.Event()

    def stop(self):
        self._halt.set()

    def _names(self):
        n = actions.tr_lower(config.cfg.get("assistant_name", "Jarvis")).strip()
        return list({n, "jarvis", "carvis", "jarviz"} - {""})

    def run(self):
        try:
            import speech_recognition as sr
            rec = sr.Recognizer()
            mic = sr.Microphone()
        except Exception:
            self.on_error("Sürekli dinleme için ses tanıma bileşeni ya da mikrofon bulunamadı.")
            return
        try:
            with mic as src:
                rec.adjust_for_ambient_noise(src, duration=1)
                while not self._halt.is_set():
                    if self.is_busy():
                        time.sleep(0.3)
                        continue
                    try:
                        audio = rec.listen(src, timeout=2, phrase_time_limit=7)
                    except sr.WaitTimeoutError:
                        continue
                    if self.is_busy():
                        continue
                    try:
                        text = rec.recognize_google(audio, language="tr-TR")
                    except Exception:
                        continue
                    low = actions.tr_lower(text)
                    hits = [(low.find(n), n) for n in self._names() if low.find(n) >= 0]
                    if not hits:
                        continue
                    idx, n = min(hits)
                    command = text[idx + len(n):].strip(" ,.!?")
                    if command:
                        self.on_command(command)
                    else:
                        self.on_wake()
        except Exception:
            self.on_error("Sürekli dinleme durdu: mikrofona erişilemedi.")
