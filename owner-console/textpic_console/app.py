"""Entry point for the TextPic Studio owner console."""

from __future__ import annotations

import os
import sys

from PySide6.QtWidgets import QApplication, QMessageBox

from . import config
from .api import ApiClient
from .dialogs import SetupDialog
from .main_window import MainWindow
from .theme import STYLESHEET


def main() -> int:
    app = QApplication(sys.argv)
    app.setApplicationName("TextPic Owner Console")
    app.setApplicationDisplayName("TextPic Studio — Owner Console")
    app.setStyleSheet(STYLESHEET)

    if not config.is_configured():
        setup = SetupDialog(first_run=True)
        if setup.exec() != SetupDialog.DialogCode.Accepted:
            QMessageBox.information(
                None,
                "TextPic Studio",
                "The console needs the server URL and the admin token before it can show "
                "customers. Run it again when you have them (docs/SETUP_SERVER.md).",
            )
            return 0

    token = config.load_token()
    smoke_path = os.environ.get("TEXTPIC_CONSOLE_SMOKE")
    if smoke_path:
        return _smoke(smoke_path, token)
    if not token:
        QMessageBox.warning(
            None,
            "No admin token",
            "The token could not be read from the Windows Credential Manager. Set it again "
            "from Settings inside the console.",
        )

    window = MainWindow(ApiClient(config.server_url(), token))
    window.show()
    return app.exec()


def _smoke(path: str, token: str) -> int:
    """Headless check of a built exe: writes its tabs and releases to `path`.

    Used by the release checklist to prove the packaged console is the new one:
    `TEXTPIC_CONSOLE_SMOKE=out.json TextPicOwner.exe`. Nothing goes on screen.
    """
    import json

    window = MainWindow(ApiClient(config.server_url(), token))
    window.show()
    QApplication.processEvents()
    releases = []
    error = ""
    try:
        window.releases_tab.refresh()
        table = window.releases_tab.table
        for row in range(table.rowCount()):
            releases.append(
                [table.item(row, column).text() for column in range(table.columnCount())]
            )
    except Exception as problem:  # noqa: BLE001 - the smoke report must survive it
        error = f"{type(problem).__name__}: {problem}"
    report = {
        "tabs": [window.tabs.tabText(i) for i in range(window.tabs.count())],
        "releasesWidget": type(window.releases_tab).__name__,
        "serverUrl": config.server_url(),
        "token": "present" if token else "missing",
        "releasesError": error,
        "releases": releases,
    }
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(report, handle, indent=2)
    window.close()
    return 0
