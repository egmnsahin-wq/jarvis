"""Asistanın beyni: cümleyi anlar, eylemi çalıştırır, doğal bir cevap kurar."""
import queue
import random
import re
import threading

import actions
import ai
import config
import phone
import voice

FAILURE_WORDS = ["bulamadım", "bulunamadı", "açılamadı", "ulaşamadım", "alınamadı", "reddetti", "sadece windows",
                 "görünmüyor", "henüz telefon", "anlayamadım", "kurulamadı", "gönderemedim"]
DATA_INTENTS = {"TIME", "DATE", "BATTERY", "NEWS", "NOTES_READ", "SELF_CHECK", "SCREENSHOT", "SHUTDOWN", "RESTART",
                "CANCEL_SHUTDOWN"}
YES = ["evet", "tabii", "tabi", "olur", "tamam", "kapat", "onaylıyorum", "yap", "aynen", "evet kapat"]
NO = ["hayır", "hayir", "vazgeç", "iptal", "yapma", "olmaz", "dur"]


def _has(t, words):
    toks = re.split(r"\W+", t)
    return any(w in toks for w in words)


class Assistant:
    def __init__(self, events):
        self.events = events
        self.history = config.load_history()
        self.pending = None
        self.speaker = voice.Speaker()
        self.busy = False
        self._run_lock = threading.Lock()

    # ------------------------------------------------------------ olaylar / konuşma
    def emit(self, *event):
        self.events.put(event)

    def say(self, reply):
        if reply and config.cfg.get("speak", True):
            self.emit("state", "speaking")
            self.speaker.say(reply)

    def notify(self, text):
        """Kullanıcıya kendiliğinden bir şey söyle (zamanlayıcı, telefondan gelen bildirim...)."""
        def work():
            with self._run_lock:
                self.busy = True
                self.emit("log", "jarvis", text)
                self.say(text)
                self.emit("state", "idle")
                self.busy = False
        threading.Thread(target=work, daemon=True).start()

    def handle_text(self, text):
        text = (text or "").strip()
        if not text:
            return
        threading.Thread(target=self._run, args=(text,), daemon=True).start()

    def _run(self, text):
        with self._run_lock:
            self.busy = True
            self.emit("log", "user", text)
            self.emit("state", "thinking")
            try:
                reply = self.process(text)
            except Exception as e:  # beklenmeyen hata: sessiz kalma, anlat
                reply = f"Bir şeyler ters gitti: {e}"
            self.emit("log", "jarvis", reply)
            self.say(reply)
            self.emit("state", "idle")
            self.busy = False

    def wake_followup(self):
        """Sadece "Jarvis" denildi: dinliyorum de ve komutu bekle."""
        def work():
            with self._run_lock:
                self.busy = True
                self.emit("state", "speaking")
                self.speaker.say(random.choice(["Dinliyorum.", "Buyur.", "Evet?", "Seni dinliyorum."]))
                self.emit("state", "listening")
                text, err = voice.listen_once(timeout=6, phrase_limit=12)
                self.busy = False
            if text:
                self.handle_text(text)
            else:
                self.emit("state", "idle")
        threading.Thread(target=work, daemon=True).start()

    # ------------------------------------------------------------ ana akış
    def process(self, text):
        t = actions.tr_lower(text).strip().strip(".!?")

        if self.pending:
            action, self.pending = self.pending, None
            if _has(t, NO):
                return "Tamam, vazgeçtim."
            if _has(t, YES):
                return actions.shutdown() if action == "SHUTDOWN" else actions.restart()

        if ai.has_key():
            res = ai.classify(text, self.history)
            if not res.get("_error"):
                reply = self.execute(text, res)
                self._remember(text, reply)
                return reply
            fallback = self.rules(t)
            if fallback:
                return self.execute(text, fallback)
            return res["reply"]

        fallback = self.rules(t)
        if fallback:
            return self.execute(text, fallback)
        return ("Henüz bir yapay zeka anahtarım yok. Ayarlar'dan ücretsiz bir Gemini anahtarı eklersen "
                "her cümleni anlayabilirim; şimdilik sadece temel komutları yapabiliyorum.")

    def _remember(self, user, reply):
        self.history.append(("user", user))
        self.history.append(("model", reply))
        self.history = self.history[-300:]
        config.save_history(self.history)

    # ------------------------------------------------------------ internetsiz yedek kurallar
    def rules(self, t):
        def R(intent, **args):
            return {"intent": intent, "reply": "", "args": args}
        if "saat kaç" in t:
            return R("TIME")
        if "ses" in t and re.search(r"\b(aç|artır|yükselt|yükselt)\w*", t) and "sessiz" not in t:
            return R("VOLUME_UP")
        if "ses" in t and re.search(r"\b(kıs|azalt|düşür)\w*", t):
            return R("VOLUME_DOWN")
        if "sessize al" in t or "sesi kapat" in t:
            return R("MUTE")
        if re.search(r"(durdur|duraklat|devam et|oynat)", t):
            return R("MEDIA_PLAY_PAUSE")
        if "sonraki" in t and ("şarkı" in t or "parça" in t):
            return R("MEDIA_NEXT")
        if "önceki" in t and ("şarkı" in t or "parça" in t):
            return R("MEDIA_PREV")
        if "kilitle" in t:
            return R("LOCK")
        m = re.match(r"^(.+?)\s+(?:aç|açar mısın|başlat)$", t)
        if m:
            return R("OPEN_APP", app=m.group(1))
        return None

    # ------------------------------------------------------------ eylemi çalıştır
    def execute(self, text, res):
        intent = (res.get("intent") or "CHAT").upper()
        a = res.get("args") or {}
        ai_reply = (res.get("reply") or "").strip()
        if intent == "CHAT":
            return ai_reply or "Buradayım."
        out = self.run_intent(intent, a, ai_reply)
        if out is None:
            return ai_reply or "Bunu henüz yapamıyorum."
        if out == ai_reply:
            return out
        failed = any(w in out.lower() for w in FAILURE_WORDS)
        if failed:
            return self.explain(text, out)
        if intent not in DATA_INTENTS and ai_reply and ai_reply.lower().strip(".") != "tamam":
            return ai_reply
        return out

    def explain(self, text, technical):
        if not ai.has_key():
            return technical
        prompt = (f"Sen {config.cfg.get('assistant_name', 'Jarvis')} adlı sesli bir asistansın. Kullanıcı şunu istedi: \"{text}\". "
                  f"Bunu yapmaya çalıştım ama sonuç şuydu: \"{technical}\". Kullanıcıya bunu en fazla 2 kısa, samimi ve doğal "
                  "Türkçe cümleyle açıkla: ne olduğunu sade anlat, mümkünse ne yapabileceğini söyle. Teknik terim, emoji ve "
                  "madde işareti kullanma; her seferinde farklı ifadeler seç. Sadece söylenecek metni yaz.")
        out = ai.ask_text(prompt)
        out = (out or "").strip().strip('"')
        return out if out and len(out) < 400 else technical

    def run_intent(self, intent, a, ai_reply=""):
        s = lambda k: (str(a.get(k)).strip() if a.get(k) not in (None, "") else "")
        table = {
            "OPEN_APP": lambda: actions.open_app(s("app")),
            "CLOSE_APP": lambda: actions.close_app(s("app")),
            "OPEN_URL": lambda: actions.open_url(s("url")),
            "SEARCH_GOOGLE": lambda: actions.search_google(s("query")),
            "SEARCH_YOUTUBE": lambda: actions.search_youtube(s("query")),
            "PLAY_SONG": lambda: actions.play_song(s("query")),
            "VOLUME_UP": actions.volume_up, "VOLUME_DOWN": actions.volume_down, "MUTE": actions.mute,
            "VOLUME_SET": lambda: actions.volume_set(a.get("level")),
            "MEDIA_PLAY_PAUSE": lambda: actions.media("MEDIA_PLAY_PAUSE"),
            "MEDIA_NEXT": lambda: actions.media("MEDIA_NEXT"),
            "MEDIA_PREV": lambda: actions.media("MEDIA_PREV"),
            "LOCK": actions.lock, "SLEEP": actions.sleep, "CANCEL_SHUTDOWN": actions.cancel_shutdown,
            "SCREENSHOT": actions.screenshot, "MINIMIZE_ALL": actions.minimize_all,
            "TIME": actions.time_now, "DATE": actions.date_today, "BATTERY": actions.battery,
            "NEWS": lambda: actions.news(s("query") or None),
            "NOTE_ADD": lambda: actions.note_add(s("text")), "NOTES_READ": actions.notes_read,
            "SELF_CHECK": self.self_check,
        }
        if intent in ("SHUTDOWN", "RESTART"):
            self.pending = intent
            return ("Bilgisayarı kapatayım mı?" if intent == "SHUTDOWN" else "Bilgisayarı yeniden başlatayım mı?") \
                + " Evet ya da hayır de."
        if intent == "TIMER":
            return self.start_timer(a)
        if intent == "PHONE":
            ok, msg = phone.run(s("phone") or None, (s("phone_action") or "").upper(), a)
            return msg
        fn = table.get(intent)
        return fn() if fn else None

    def start_timer(self, a):
        try:
            minutes = float(a.get("minutes"))
        except Exception:
            return "Kaç dakika sonra hatırlatayım, anlayamadım."
        msg = str(a.get("message") or "").strip()
        text = f"Hatırlatma: {msg}" if msg else "Süre doldu."
        timer = threading.Timer(minutes * 60, lambda: self.notify(text))
        timer.daemon = True
        timer.start()
        return f"{minutes:g} dakika sonra hatırlatacağım."

    # ------------------------------------------------------------ kendini kontrol
    def self_check(self):
        problems = []
        if not ai.has_key():
            problems.append("hiç Gemini anahtarı girilmemiş, Ayarlar'dan ücretsiz bir anahtar eklemen yeterli")
        else:
            r = ai.test_key(config.cfg["gemini_key"])
            if r.startswith("✗"):
                problems.append("Gemini anahtarında sorun var: " + r[1:].strip().rstrip("."))
        try:
            import speech_recognition  # noqa: F401
        except Exception:
            problems.append("ses tanıma bileşeni yüklenemedi")
        for p in config.cfg["phones"]:
            if p.get("ip", "").strip():
                ok, _ = phone.send(p["ip"], {"action": "PING"}, timeout=3)
                if not ok:
                    problems.append(f"{p.get('name') or p['ip']} telefonuna ulaşamıyorum")
        if not problems:
            return random.choice(["Kendimi kontrol ettim, her şey yolunda görünüyor.",
                                  "Bir baktım, anahtar, mikrofon ve telefon bağlantısı tamam."])
        return random.choice(["Kontrol ettim, şunları fark ettim: ", "Şu sorunlar var: "]) + "; ".join(problems) + "."

    # ------------------------------------------------------------ telefondan gelen komutlar
    def run_remote(self, payload, ip=""):
        act = str(payload.get("action", "")).upper()
        g = lambda k: str(payload.get(k, "") or "").strip()
        self.emit("log", "sistem", f"Telefondan komut: {act} ({ip})")
        mapping = {
            "PING": lambda: f"pong ({config.cfg.get('assistant_name', 'Jarvis')} Windows)",
            "OPEN_APP": lambda: actions.open_app(g("app")),
            "OPEN_URL": lambda: actions.open_url(g("url")),
            "SEARCH_GOOGLE": lambda: actions.search_google(g("query")),
            "SEARCH_YOUTUBE": lambda: actions.search_youtube(g("query")),
            "PLAY_SONG": lambda: actions.play_song(g("query")),
            "SHUTDOWN": actions.shutdown, "RESTART": actions.restart, "SLEEP": actions.sleep, "LOCK": actions.lock,
            "CANCEL_SHUTDOWN": actions.cancel_shutdown,
            "VOLUME_UP": actions.volume_up, "VOLUME_DOWN": actions.volume_down, "MUTE": actions.mute,
            "MEDIA_PLAY_PAUSE": lambda: actions.media("MEDIA_PLAY_PAUSE"),
            "MEDIA_NEXT": lambda: actions.media("MEDIA_NEXT"),
            "MEDIA_PREV": lambda: actions.media("MEDIA_PREV"),
            "SCREENSHOT": actions.screenshot, "MINIMIZE_ALL": actions.minimize_all,
        }
        if act == "NOTIFY":
            self.notify(g("message") or "Telefondan bir mesaj var.")
            return "Bilgisayarda gösterildi ve okundu."
        fn = mapping.get(act)
        return fn() if fn else f"Bilinmeyen komut: {act}"
