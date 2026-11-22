"""Telefondan gelen bilgisayar komutlarını dinler (port 8766, telefondaki PcController ile aynı protokol).

İstek:  POST /command   {"action": "OPEN_APP", "app": "chrome"}
Cevap:  {"ok": true, "message": "..."}
"""
import json
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

import config


def make_handler(on_command):
    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *args):
            pass

        def _send(self, status, ok, message):
            body = json.dumps({"ok": ok, "message": message}, ensure_ascii=False).encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def do_POST(self):
            if self.path != "/command":
                return self._send(404, False, "Bilinmeyen adres.")
            ip = self.client_address[0]
            allowed = {p["ip"].strip() for p in config.cfg["phones"] if p.get("ip", "").strip()}
            if config.cfg.get("only_registered") and allowed and ip not in allowed and ip != "127.0.0.1":
                return self._send(403, False, "Bu cihaz kayıtlı değil. Bilgisayarda Ayarlar'dan telefonu ekle.")
            try:
                length = int(self.headers.get("Content-Length", "0"))
                payload = json.loads(self.rfile.read(length).decode("utf-8") or "{}")
            except Exception:
                return self._send(400, False, "Geçersiz JSON.")
            try:
                message = on_command(payload, ip)
                self._send(200, True, message)
            except Exception as e:
                self._send(500, False, f"Komut çalıştırılamadı: {e}")

    return Handler


def start(on_command):
    """Sunucuyu arka plan iş parçacığında başlatır. (sunucu, hata_mesajı) döndürür."""
    try:
        httpd = ThreadingHTTPServer(("0.0.0.0", config.SERVER_PORT), make_handler(on_command))
    except OSError as e:
        return None, f"Port {config.SERVER_PORT} açılamadı ({e}). Başka bir Jarvis açık olabilir."
    threading.Thread(target=httpd.serve_forever, daemon=True).start()
    return httpd, None
