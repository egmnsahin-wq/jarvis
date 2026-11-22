import os
import queue
import sys
import tkinter as tk
import traceback

import config
import core
import server
import ui


def _log_error(text):
    try:
        with open(os.path.join(config.APP_DIR, "hata.log"), "a", encoding="utf-8") as f:
            f.write(text + "\n")
    except Exception:
        pass


def main():
    sys.excepthook = lambda t, v, tb: _log_error("".join(traceback.format_exception(t, v, tb)))
    events = queue.Queue()
    assistant = core.Assistant(events)
    httpd, server_error = server.start(assistant.run_remote)
    root = tk.Tk()
    root.report_callback_exception = lambda t, v, tb: _log_error("".join(traceback.format_exception(t, v, tb)))
    ui.App(root, assistant, events, server_error)
    root.mainloop()
    if httpd:
        httpd.shutdown()


if __name__ == "__main__":
    main()
