"""Headless smoke test of the console window against a running server.

    set QT_QPA_PLATFORM=offscreen
    python tools/ui_smoke.py http://127.0.0.1:8787 <admin-token>

Builds the real window, loads the real data, opens the create-key dialog and
prints what it saw. Nothing is shown on screen.
"""

from __future__ import annotations

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from PySide6.QtWidgets import QApplication  # noqa: E402

from textpic_console.api import ApiClient  # noqa: E402
from textpic_console.dialogs import CreateKeyDialog  # noqa: E402
from textpic_console.main_window import MainWindow  # noqa: E402
from textpic_console.theme import STYLESHEET  # noqa: E402


def main() -> int:
    base = sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:8787"
    token = sys.argv[2] if len(sys.argv) > 2 else ""
    if not token:
        print("usage: ui_smoke.py BASE_URL ADMIN_TOKEN")
        return 2

    app = QApplication([])
    app.setStyleSheet(STYLESHEET)
    client = ApiClient(base, token)

    window = MainWindow(client)
    window.show()
    app.processEvents()

    print(f"table rows: {window.table.rowCount()}")
    print(f"stats total: {window.stat_labels['total'].text()}")
    print(f"connection: {window.connection_label.text()}")

    if window.table.rowCount() > 0:
        window.table.selectRow(0)
        app.processEvents()
        print(f"details title: {window.details_title.text()}")
        print(f"details key: {window.detail_values['key'].text()[:9]}…")
        print(f"history: {window.events_label.text().splitlines()[:1]}")

    dialog = CreateKeyDialog(client, window)
    dialog.name_field.setText("Smoke Test")
    dialog.device_field.setText("tp 5rkd 8wqz 3mnb 6ytf")
    app.processEvents()
    print(f"create button enabled: {dialog.ok_button.isEnabled()}")
    print(f"device hint: {dialog.device_hint.text()}")
    print(f"summary: {dialog.summary.text()}")
    dialog.close()

    window.close()
    print("UI OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
