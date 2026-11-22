"""Ayarların ve konuşma geçmişinin kalıcı saklanması (%APPDATA%\\JarvisMasaustu)."""
import json
import os

APP_DIR = os.path.join(os.environ.get("APPDATA", os.path.expanduser("~")), "JarvisMasaustu")
os.makedirs(APP_DIR, exist_ok=True)
CONFIG_PATH = os.path.join(APP_DIR, "config.json")
HISTORY_PATH = os.path.join(APP_DIR, "history.json")
NOTES_PATH = os.path.join(APP_DIR, "notlar.txt")

SERVER_PORT = 8766   # Telefon uygulamasındaki PcController bu portu kullanır
PHONE_PORT = 8765    # Telefondaki "uzaktan kontrol" sunucusu

DEFAULTS = {
    "gemini_key": "",
    "gemini_model": "",
    "assistant_name": "Jarvis",
    "phones": [{"name": "", "ip": ""}, {"name": "", "ip": ""}, {"name": "", "ip": ""}],
    "speak": True,
    "voice": "tr-TR-EmelNeural",
    "wake_listen": False,
    "only_registered": True,
}

cfg = dict(DEFAULTS)


def load():
    global cfg
    data = {}
    try:
        with open(CONFIG_PATH, encoding="utf-8") as f:
            data = json.load(f)
    except Exception:
        pass
    cfg = {**DEFAULTS, **data}
    phones = cfg.get("phones") or []
    while len(phones) < 3:
        phones.append({"name": "", "ip": ""})
    cfg["phones"] = phones[:3]
    return cfg


def save():
    try:
        with open(CONFIG_PATH, "w", encoding="utf-8") as f:
            json.dump(cfg, f, ensure_ascii=False, indent=2)
    except Exception:
        pass


def load_history():
    try:
        with open(HISTORY_PATH, encoding="utf-8") as f:
            return [tuple(x) for x in json.load(f)]
    except Exception:
        return []


def save_history(history):
    try:
        with open(HISTORY_PATH, "w", encoding="utf-8") as f:
            json.dump(history[-300:], f, ensure_ascii=False)
    except Exception:
        pass


load()
