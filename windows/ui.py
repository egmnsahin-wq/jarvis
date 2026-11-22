"""Telefon sürümüne benzeyen koyu HUD arayüzü (tkinter, ek kütüphane gerektirmez)."""
import math
import queue
import socket
import threading
import tkinter as tk
import webbrowser
from tkinter import ttk

import ai
import config
import phone
import voice

BG, PANEL, FG, DIM = "#0A0E14", "#111826", "#E6EEF7", "#7C8BA1"
BLUE, GREEN, ORANGE, RED = "#00D4FF", "#2EE59D", "#FF9F1C", "#FF5C5C"
STATE_COLORS = {"idle": GREEN, "listening": BLUE, "thinking": ORANGE, "speaking": "#7C9CFF"}
STATE_TEXT = {"idle": "HAZIR", "listening": "DİNLİYORUM...", "thinking": "DÜŞÜNÜYORUM...", "speaking": "KONUŞUYORUM..."}


def local_ip():
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return "bulunamadı"


class App:
    def __init__(self, root, assistant, events, server_error=None):
        self.root, self.assistant, self.events = root, assistant, events
        self.state, self.angle = "idle", 0.0
        self.wake = None
        root.title(config.cfg["assistant_name"].upper() + " — Masaüstü")
        root.geometry("880x720")
        root.minsize(640, 560)
        root.configure(bg=BG)
        self._build()
        self._animate()
        self._poll()
        name = config.cfg["assistant_name"]
        if server_error:
            self._log("sistem", server_error)
        self._log("sistem", f"Telefondan komut almak için bu bilgisayarın IP adresi: {local_ip()}  (port {config.SERVER_PORT})")
        if not ai.has_key():
            self._log("jarvis", f"Merhaba! Başlamak için sağ üstteki Ayarlar'dan ücretsiz bir Gemini anahtarı eklemen yeterli; "
                                f"orada nasıl alacağını adım adım yazdım.")
            root.after(600, self.open_settings)
        else:
            self._log("jarvis", f"Merhaba, ben {name}. Mikrofona dokun ya da bir şey yaz.")

    # ------------------------------------------------------------ arayüz
    def _build(self):
        top = tk.Frame(self.root, bg=BG)
        top.pack(fill="x", padx=16, pady=(14, 0))
        self.title = tk.Label(top, text=config.cfg["assistant_name"].upper(), fg=BLUE, bg=BG, font=("Segoe UI Light", 30))
        self.title.pack(side="left")
        tk.Button(top, text="⚙  Ayarlar", command=self.open_settings, bg=PANEL, fg=FG, relief="flat",
                  activebackground=BLUE, padx=12, pady=6).pack(side="right")

        self.status = tk.Label(self.root, text=STATE_TEXT["idle"], fg=DIM, bg=BG, font=("Segoe UI", 11))
        self.status.pack()

        self.canvas = tk.Canvas(self.root, width=260, height=260, bg=BG, highlightthickness=0)
        self.canvas.pack(pady=4)

        wake_row = tk.Frame(self.root, bg=BG)
        wake_row.pack()
        self.wake_var = tk.BooleanVar(value=False)
        tk.Checkbutton(wake_row, text="Sürekli dinle (\"Jarvis\" diyerek uyandır)", variable=self.wake_var,
                       command=self.toggle_wake, bg=BG, fg=FG, selectcolor=PANEL, activebackground=BG,
                       activeforeground=FG).pack()

        frame = tk.Frame(self.root, bg=PANEL)
        frame.pack(fill="both", expand=True, padx=16, pady=10)
        self.log = tk.Text(frame, bg=PANEL, fg=FG, relief="flat", wrap="word", font=("Segoe UI", 11),
                           state="disabled", padx=12, pady=8)
        sb = ttk.Scrollbar(frame, command=self.log.yview)
        self.log.configure(yscrollcommand=sb.set)
        sb.pack(side="right", fill="y")
        self.log.pack(side="left", fill="both", expand=True)
        self.log.tag_config("user", foreground=DIM)
        self.log.tag_config("jarvis", foreground=FG)
        self.log.tag_config("sistem", foreground=ORANGE, font=("Segoe UI", 9))

        bottom = tk.Frame(self.root, bg=BG)
        bottom.pack(fill="x", padx=16, pady=(0, 16))
        self.entry = tk.Entry(bottom, bg=PANEL, fg=FG, insertbackground=FG, relief="flat", font=("Segoe UI", 12))
        self.entry.pack(side="left", fill="x", expand=True, ipady=9, padx=(0, 8))
        self.entry.bind("<Return>", lambda e: self.send())
        tk.Button(bottom, text="Gönder", command=self.send, bg=BLUE, fg="black", relief="flat", padx=14, pady=8).pack(side="left")
        self.mic = tk.Button(bottom, text="🎤", command=self.mic_once, bg=ORANGE, fg="black", relief="flat",
                             font=("Segoe UI", 13), padx=14, pady=4)
        self.mic.pack(side="left", padx=(8, 0))
        self.entry.focus_set()

    def _log(self, who, text):
        self.log.configure(state="normal")
        prefix = {"user": "Sen: ", "jarvis": config.cfg["assistant_name"] + ": ", "sistem": "• "}.get(who, "")
        self.log.insert("end", prefix + text + "\n\n", who)
        self.log.configure(state="disabled")
        self.log.see("end")

    # ------------------------------------------------------------ animasyon ve olaylar
    def _animate(self):
        c, col = self.canvas, STATE_COLORS.get(self.state, GREEN)
        c.delete("all")
        cx = cy = 130
        speed = {"idle": 1.2, "listening": 3.5, "thinking": 6, "speaking": 4}.get(self.state, 1.2)
        self.angle = (self.angle + speed) % 360
        pulse = 1 + 0.06 * math.sin(math.radians(self.angle * 3))
        c.create_oval(cx - 70 * pulse, cy - 70 * pulse, cx + 70 * pulse, cy + 70 * pulse, fill=col, outline="")
        c.create_oval(cx - 28, cy - 28, cx + 28, cy + 28, fill="#CFFFE8" if self.state == "idle" else "#FFFFFF", outline="")
        for i, (r, ext, off) in enumerate([(95, 90, 0), (115, 60, 120), (125, 40, 240)]):
            d = 1 if i % 2 == 0 else -1
            c.create_arc(cx - r, cy - r, cx + r, cy + r, start=self.angle * d + off, extent=ext, style="arc",
                         outline=col, width=3)
        self.root.after(40, self._animate)

    def _poll(self):
        try:
            while True:
                ev = self.events.get_nowait()
                if ev[0] == "log":
                    self._log(ev[1], ev[2])
                elif ev[0] == "state":
                    self.state = ev[1]
                    self.status.config(text=STATE_TEXT.get(ev[1], ""))
        except queue.Empty:
            pass
        self.root.after(80, self._poll)

    # ------------------------------------------------------------ eylemler
    def send(self):
        text = self.entry.get().strip()
        if text:
            self.entry.delete(0, "end")
            self.assistant.handle_text(text)

    def mic_once(self):
        if self.assistant.busy:
            return

        def work():
            self.events.put(("state", "listening"))
            text, err = voice.listen_once()
            if text:
                self.assistant.handle_text(text)
            else:
                self.events.put(("log", "sistem", err or "Duyamadım."))
                self.events.put(("state", "idle"))
        threading.Thread(target=work, daemon=True).start()

    def toggle_wake(self):
        config.cfg["wake_listen"] = self.wake_var.get()
        config.save()
        if self.wake_var.get():
            self.wake = voice.WakeLoop(lambda: self.assistant.busy, self.assistant.handle_text, self.assistant.wake_followup,
                                       lambda m: (self.events.put(("log", "sistem", m)), self.wake_var.set(False)))
            self.wake.start()
            self._log("sistem", f"Sürekli dinleme açık: \"{config.cfg['assistant_name']}, ...\" diyerek komut ver.")
        elif self.wake:
            self.wake.stop()
            self.wake = None
            self._log("sistem", "Sürekli dinleme kapandı.")

    # ------------------------------------------------------------ ayarlar penceresi
    def open_settings(self):
        w = tk.Toplevel(self.root)
        w.title("Ayarlar")
        w.configure(bg=BG)
        w.geometry("640x700")
        w.transient(self.root)

        def label(text, color=FG, size=10, bold=False, pady=(10, 0)):
            tk.Label(w, text=text, fg=color, bg=BG, font=("Segoe UI", size, "bold" if bold else "normal"),
                     justify="left", wraplength=590, anchor="w").pack(fill="x", padx=18, pady=pady)

        def entry(var, show=None):
            e = tk.Entry(w, textvariable=var, show=show, bg=PANEL, fg=FG, insertbackground=FG, relief="flat",
                         font=("Segoe UI", 11))
            e.pack(fill="x", padx=18, ipady=6)
            return e

        label("Gemini API anahtarı", BLUE, 12, True)
        key_var = tk.StringVar(value=config.cfg["gemini_key"])
        entry(key_var, "•")
        label("Tek bu anahtar yeter, başka hiçbir anahtar gerekmez.\n"
              "1) \"Anahtar al\"a tıkla, Google hesabınla giriş yap.\n"
              "2) \"Create API key\" düğmesine bas.\n"
              "3) Çıkan AIza... anahtarını kopyalayıp yukarıya yapıştır, Kaydet'e bas. Ücretsizdir, kart istemez.",
              DIM, 9, pady=(4, 0))
        row = tk.Frame(w, bg=BG)
        row.pack(fill="x", padx=18, pady=6)
        result = tk.Label(w, text="", fg=DIM, bg=BG, font=("Segoe UI", 9), wraplength=590, justify="left", anchor="w")

        def test_key():
            result.config(text="Deneniyor...")
            k = key_var.get()
            threading.Thread(target=lambda: self.root.after(0, lambda: result.config(text=ai.test_key(k))), daemon=True).start()

        tk.Button(row, text="Anahtar al", command=lambda: webbrowser.open("https://aistudio.google.com/apikey"),
                  bg=BLUE, fg="black", relief="flat", padx=12).pack(side="left")
        tk.Button(row, text="Anahtarı test et", command=test_key, bg=PANEL, fg=BLUE, relief="flat", padx=12).pack(side="left", padx=8)
        result.pack(fill="x", padx=18)

        label("Asistan adı", FG, 10, True)
        name_var = tk.StringVar(value=config.cfg["assistant_name"])
        entry(name_var)

        label("Telefonlar (en fazla 3)", BLUE, 12, True, pady=(18, 0))
        label(f"Bu bilgisayarın IP adresi: {local_ip()}  — telefondaki Jarvis'in Ayarlar > \"Bilgisayar IP Adresi\" kutusuna bunu yaz.\n"
              "Telefonda Ayarlar > \"Uzaktan kontrole aç\" açık olmalı ve ikisi aynı Wi-Fi'de olmalı. Telefonun IP'sini "
              "telefondaki Jarvis Ayarlar ekranının altındaki \"Bu telefonun IP adresi\" satırında görebilirsin.", DIM, 9, pady=(2, 4))
        phone_vars = []
        for p in config.cfg["phones"]:
            r = tk.Frame(w, bg=BG)
            r.pack(fill="x", padx=18, pady=3)
            nv, iv = tk.StringVar(value=p.get("name", "")), tk.StringVar(value=p.get("ip", ""))
            phone_vars.append((nv, iv))
            tk.Entry(r, textvariable=nv, bg=PANEL, fg=FG, insertbackground=FG, relief="flat", width=16,
                     font=("Segoe UI", 11)).pack(side="left", ipady=5)
            tk.Entry(r, textvariable=iv, bg=PANEL, fg=FG, insertbackground=FG, relief="flat", width=18,
                     font=("Segoe UI", 11)).pack(side="left", padx=6, ipady=5)
            st = tk.Label(r, text="", fg=DIM, bg=BG, font=("Segoe UI", 9))

            def test(iv=iv, st=st):
                st.config(text="...")
                def work():
                    ok, msg = phone.send(iv.get(), {"action": "PING"}, timeout=4)
                    self.root.after(0, lambda: st.config(text=("✓ bağlandı" if ok else "✗ ulaşılamadı"), fg=GREEN if ok else RED))
                threading.Thread(target=work, daemon=True).start()
            tk.Button(r, text="Test", command=test, bg=PANEL, fg=BLUE, relief="flat").pack(side="left")
            st.pack(side="left", padx=6)

        only_var = tk.BooleanVar(value=config.cfg["only_registered"])
        speak_var = tk.BooleanVar(value=config.cfg["speak"])
        for text, var in [("Yalnızca kayıtlı telefonlar bu bilgisayara komut verebilsin (önerilir)", only_var),
                          ("Cevapları sesli oku", speak_var)]:
            tk.Checkbutton(w, text=text, variable=var, bg=BG, fg=FG, selectcolor=PANEL, activebackground=BG,
                           activeforeground=FG, anchor="w").pack(fill="x", padx=14, pady=(8, 0))

        def save():
            config.cfg["gemini_key"] = key_var.get().strip()
            config.cfg["assistant_name"] = name_var.get().strip() or "Jarvis"
            config.cfg["phones"] = [{"name": n.get().strip(), "ip": i.get().strip()} for n, i in phone_vars]
            config.cfg["only_registered"] = only_var.get()
            config.cfg["speak"] = speak_var.get()
            config.save()
            self.title.config(text=config.cfg["assistant_name"].upper())
            self._log("sistem", "Ayarlar kaydedildi.")
            w.destroy()

        tk.Button(w, text="Kaydet", command=save, bg=BLUE, fg="black", relief="flat", padx=24, pady=8,
                  font=("Segoe UI", 11, "bold")).pack(pady=18)
