"""Telefondaki Jarvis'in uzaktan kontrol sunucusuna (port 8765) komut gönderir."""
import json
import urllib.error
import urllib.request

import config
import actions


def find_phone(name=None):
    phones = [p for p in config.cfg["phones"] if p.get("ip", "").strip()]
    if not phones:
        return None
    if name:
        n = actions.tr_lower(name)
        for p in phones:
            pn = actions.tr_lower(p.get("name", ""))
            if pn and (pn in n or n in pn):
                return p
    return phones[0]


def send(ip, payload, timeout=6):
    """Telefona komut yollar; (başarılı_mı, mesaj) döndürür."""
    try:
        req = urllib.request.Request(f"http://{ip.strip()}:{config.PHONE_PORT}/command",
                                     data=json.dumps(payload).encode("utf-8"), method="POST",
                                     headers={"Content-Type": "application/json"})
        with urllib.request.urlopen(req, timeout=timeout) as r:
            data = json.loads(r.read().decode("utf-8", "replace") or "{}")
            return bool(data.get("ok", True)), data.get("message") or "Telefona iletildi."
    except urllib.error.HTTPError as e:
        try:
            return False, json.loads(e.read().decode()).get("message", f"Telefon reddetti ({e.code}).")
        except Exception:
            return False, f"Telefon reddetti ({e.code})."
    except Exception:
        return False, ("Telefona ulaşamadım. Telefonda Jarvis'in Ayarlar'ında \"uzaktan kontrole aç\" açık mı, "
                       "aynı Wi-Fi'de misiniz, IP doğru mu?")


def run(phone_name, action, args):
    p = find_phone(phone_name)
    if not p:
        return False, "Henüz telefon eklenmemiş. Ayarlar'dan telefonunun IP adresini ekleyebilirsin."
    payload = {"action": action}
    for k in ("contact", "message", "app", "query", "hour", "minute", "minutes"):
        if args.get(k) not in (None, ""):
            payload[k] = args[k]
    return send(p["ip"], payload)
