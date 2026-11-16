# Third-party software in this folder

This console is a separate Windows program on the owner's PC. It is **not** part of
the Android app.

* **Python 3.12** — PSF License — https://docs.python.org/3/license.html
* **PySide6 / Qt 6** — LGPL-3.0 — https://www.gnu.org/licenses/lgpl-3.0.html
  Qt libraries ship as separate DLLs inside `_internal/`, so they can be replaced
  with a compatible build of your own (as the LGPL requires).
* **keyring** — MIT License — https://github.com/jaraco/keyring
* **PyInstaller** — GPL-2.0 with a special exception that allows packaging
  proprietary programs — https://pyinstaller.org

Your own code (this console, the app and the server) is not affected by these
licences.
