"""Gemini istemcisi: tek bir ücretsiz Google anahtarı her şeye yeter.

Model adları Google tarafından zamanla kapatıldığı için sabit değildir: son çalışan model, sonra "-latest"
takma adları ve bilinen modeller denenir; 404/429/5xx'te sıradakine geçilir, hiçbiri olmazsa Google'ın
model listesinden uygun bir flash model seçilir.
"""
import json
import random
import re
import urllib.error
import urllib.request

import config

BASE = "https://generativelanguage.googleapis.com/v1beta"
DEFAULT_MODELS = [
    "gemini-flash-lite-latest",
    "gemini-2.5-flash-lite",
    "gemini-flash-latest",
    "gemini-3.1-flash-lite",
    "gemini-2.5-flash",
]

LIMIT_REPLIES = [
    "Ücretsiz kullanım limitime takıldım, şimdilik daha fazla düşünemiyorum. Biraz dinlendikten sonra tekrar seninle konuşmak isterim.",
    "Ücretsiz kullanım limitime takıldım galiba. Bir süre sonra tekrar dener misin, seninle konuşmaya devam etmek isterim.",
    "Ücretsiz kullanım limitime takıldım, biraz mola vermem lazım. Sonra yine sohbet edelim mi?",
]
UNREACHABLE_REPLIES = [
    "Şu an yapay zekaya ulaşamadım, internetine bir bakıp biraz sonra tekrar dener misin?",
    "Bir türlü bağlanamadım, ağda bir sorun olabilir. Birazdan tekrar deneyelim mi?",
    "Yapay zeka tarafına ulaşamıyorum şu an. Bağlantını kontrol edip bana tekrar söyler misin?",
]


def has_key():
    return bool(config.cfg.get("gemini_key", "").strip())


def _request(method, url, key, body=None, timeout=40):
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(url, data=data, method=method,
                                 headers={"Content-Type": "application/json", "x-goog-api-key": key})
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return resp.status, resp.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace")


def _should_try_next(code, text):
    return (code in (404, 429) or 500 <= code < 600 or
            (code == 400 and ("not found" in text.lower() or "not supported" in text.lower())))


def _discover(key):
    try:
        code, text = _request("GET", f"{BASE}/models?pageSize=200", key, timeout=20)
        if code != 200:
            return []
        out = []
        for m in json.loads(text).get("models", []):
            name = m.get("name", "").replace("models/", "")
            if ("generateContent" in m.get("supportedGenerationMethods", []) and "flash" in name and
                    not any(x in name for x in ("image", "tts", "live", "audio", "embedding", "robotics", "computer"))):
                out.append(name)
        out.sort(key=lambda n: ("lite" not in n, "preview" in n, n))
        return out
    except Exception:
        return []


def generate(key, body):
    """(http_kodu, gövde) döndürür. Ağ yoksa ConnectionError fırlatır."""
    cached = config.cfg.get("gemini_model", "")
    order = []
    for m in [cached] + DEFAULT_MODELS:
        if m and m not in order:
            order.append(m)
    tried, last = set(), (0, "")

    def attempt(model):
        nonlocal last
        tried.add(model)
        try:
            code, text = _request("POST", f"{BASE}/models/{model}:generateContent", key, body)
        except Exception as e:
            raise ConnectionError(str(e))
        if 200 <= code < 300:
            if config.cfg.get("gemini_model") != model:
                config.cfg["gemini_model"] = model
                config.save()
            return code, text
        last = (401, text) if code == 400 and "api key not valid" in text.lower() else (code, text)
        return None if _should_try_next(code, text) else last

    for m in order:
        r = attempt(m)
        if r:
            return r
    for m in [x for x in _discover(key) if x not in tried][:4]:
        r = attempt(m)
        if r:
            return r
    return last


def _extract(text):
    try:
        parts = json.loads(text)["candidates"][0]["content"]["parts"]
        return "".join(p.get("text", "") for p in parts).strip() or None
    except Exception:
        return None


def test_key(key):
    key = (key or "").strip()
    if not key:
        return "Önce anahtarı yapıştır."
    body = {"contents": [{"role": "user", "parts": [{"text": "Sadece 'tamam' yaz."}]}],
            "generationConfig": {"maxOutputTokens": 64}}
    try:
        code, _ = generate(key, body)
    except ConnectionError:
        return "✗ İnternete ulaşamadım. Bağlantını kontrol edip tekrar dene."
    if 200 <= code < 300:
        return f"✓ Anahtar çalışıyor (model: {config.cfg.get('gemini_model')})."
    if code in (401, 403):
        return "✗ Anahtar geçersiz görünüyor. Baştan sona kopyaladığından ve boşluk girmediğinden emin ol."
    if code == 429:
        return "✗ Anahtar geçerli ama şu an ücretsiz limit dolu. Biraz sonra tekrar dene."
    return f"✗ Beklenmeyen yanıt ({code})."


SYSTEM_PROMPT = """Sen {name} adında, Türkçe konuşan, samimi ve zeki bir sesli asistansın; Windows bilgisayarda çalışıyorsun
ve kullanıcının telefonuna da komut gönderebilirsin. Kullanıcının cümlesini anla ve SADECE şu JSON'u döndür:
{{"intent":"<INTENT>","reply":"<kullanıcıya söylenecek kısa, doğal cümle>","args":{{...}}}}

INTENT listesi ve args alanları:
OPEN_APP{{app}} CLOSE_APP{{app}} OPEN_URL{{url}} SEARCH_GOOGLE{{query}} SEARCH_YOUTUBE{{query}} PLAY_SONG{{query}}
VOLUME_UP VOLUME_DOWN VOLUME_SET{{level:0-100}} MUTE MEDIA_PLAY_PAUSE MEDIA_NEXT MEDIA_PREV
LOCK SLEEP SHUTDOWN RESTART CANCEL_SHUTDOWN SCREENSHOT MINIMIZE_ALL
TIME DATE BATTERY NEWS{{query?}} TIMER{{minutes,message}} NOTE_ADD{{text}} NOTES_READ SELF_CHECK
PHONE{{phone?,phone_action,contact?,message?,app?,query?,hour?,minute?,minutes?}} CHAT

PHONE: kullanıcı telefondan bir şey isterse (arama, mesaj, WhatsApp, fener, telefonu bul, telefona bildirim, telefonda
uygulama/şarkı aç, alarm, hatırlatıcı, pil). phone_action şunlardan biri: CALL SMS WHATSAPP FLASHLIGHT_ON FLASHLIGHT_OFF
FIND_PHONE NOTIFY OPEN_APP PLAY_SONG SEARCH_GOOGLE SEARCH_YOUTUBE SET_ALARM REMINDER BATTERY_STATUS.
phone: birden fazla telefon varsa söylenen ad, yoksa null.

KURALLAR:
- Metin sesli tanımadan geliyor: yanlış duyulmuş kelimeler, eksik ekler olabilir; kelimesi kelimesine değil NİYETE göre karar ver.
- Önceki konuşmayı hatırla ("onu da kapat", "aynısını tekrar aç" gibi göndermeleri geçmişe bakarak çöz).
- Kritik bilgi eksikse (kime? ne yazayım?) eylem yapma, intent CHAT ile tek kısa soru sor.
- Genel soru, sohbet, bilgi, tavsiye, özet, kod vb. için de doğal ve yardımsever cevap ver (CHAT).
- reply sesli okunacak: kısa, akıcı, emoji/madde işareti/markdown YOK; her seferinde farklı kalıplarla, insan gibi konuş.
- Uygulama adlarını bilgisayardaki gibi yaz (örn. "chrome", "spotify", "hesap makinesi", "not defteri").
- Asla uydurma bilgi verme; emin değilsen args alanlarını boş bırak.
"""


def parse_intent(raw):
    raw = raw.strip()
    raw = re.sub(r"^```(?:json)?|```$", "", raw, flags=re.M).strip()
    try:
        d = json.loads(raw)
    except Exception:
        m = re.search(r"\{.*\}", raw, re.S)
        try:
            d = json.loads(m.group(0)) if m else None
        except Exception:
            d = None
    if not isinstance(d, dict):
        return {"intent": "CHAT", "reply": raw[:600] or "Anlayamadım.", "args": {}}
    d.setdefault("intent", "CHAT")
    d.setdefault("reply", "")
    if not isinstance(d.get("args"), dict):
        d["args"] = {}
    return d


def window_history(history):
    picked, chars = [], 0
    for role, text in reversed(history[-60:]):
        chars += len(text)
        if chars > 14000 and picked:
            break
        picked.append((role, text))
    return list(reversed(picked))


def classify(text, history):
    """Cümleyi yapay zekaya anlatır. Başarısızsa {'_error': True, 'reply': ...} döner."""
    key = config.cfg.get("gemini_key", "").strip()
    contents = [{"role": "user", "parts": [{"text": SYSTEM_PROMPT.format(name=config.cfg.get("assistant_name", "Jarvis"))}]}]
    for role, t in window_history(history):
        contents.append({"role": "user" if role == "user" else "model", "parts": [{"text": t}]})
    contents.append({"role": "user", "parts": [{"text": text}]})
    body = {"contents": contents,
            "generationConfig": {"temperature": 0.8, "maxOutputTokens": 2000, "responseMimeType": "application/json"}}
    try:
        code, resp = generate(key, body)
    except ConnectionError:
        return {"_error": True, "intent": "CHAT", "reply": random.choice(UNREACHABLE_REPLIES), "args": {}}
    if not 200 <= code < 300:
        if code == 429:
            msg = random.choice(LIMIT_REPLIES)
        elif code in (401, 403):
            msg = "Gemini anahtarım geçersiz görünüyor. Ayarlar'dan anahtarı kontrol eder misin?"
        else:
            msg = random.choice(UNREACHABLE_REPLIES)
        return {"_error": True, "intent": "CHAT", "reply": msg, "args": {}}
    out = _extract(resp)
    if not out:
        return {"_error": True, "intent": "CHAT", "reply": "Bir cevap alamadım, tekrar dener misin?", "args": {}}
    return parse_intent(out)


def ask_text(prompt):
    """Serbest metin sorusu (hata açıklama vb.). Başarısızsa None."""
    key = config.cfg.get("gemini_key", "").strip()
    if not key:
        return None
    body = {"contents": [{"role": "user", "parts": [{"text": prompt}]}],
            "generationConfig": {"temperature": 0.7, "maxOutputTokens": 1024}}
    try:
        code, resp = generate(key, body)
    except ConnectionError:
        return None
    return _extract(resp) if 200 <= code < 300 else None
