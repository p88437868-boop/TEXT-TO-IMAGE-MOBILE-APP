"""The owner console window: customers, details, actions and settings."""

from __future__ import annotations

from datetime import datetime, timezone
from zoneinfo import available_timezones

from PySide6.QtCore import Qt, QTimer
from PySide6.QtGui import QColor, QGuiApplication
from PySide6.QtWidgets import (
    QAbstractItemView,
    QComboBox,
    QFileDialog,
    QFrame,
    QGridLayout,
    QHBoxLayout,
    QHeaderView,
    QLabel,
    QLineEdit,
    QMainWindow,
    QMessageBox,
    QPushButton,
    QScrollArea,
    QSplitter,
    QTableWidget,
    QTableWidgetItem,
    QTabWidget,
    QVBoxLayout,
    QWidget,
)

from . import config
from .api import ApiClient, ApiError, OfflineError
from .dialogs import (
    AddImagesDialog,
    CreateKeyDialog,
    DateExtendDialog,
    EditDialog,
    ExtendDialog,
    KeyResultDialog,
    MovePhoneDialog,
    PlanDialog,
    SetupDialog,
)
from .formatting import (
    end_date_label,
    format_datetime,
    format_ts,
    mask_key,
    plan_label,
    relative_time,
    status_color,
    status_label,
)
from .theme import COLORS

COLUMNS = [
    "Name",
    "Key",
    "Status",
    "Device Code",
    "Phone",
    "Android",
    "App",
    "Plan",
    "Used today",
    "Used total",
    "Remaining",
    "Created",
    "Activated",
    "Ends on",
    "Last seen",
    "Note",
]

STAT_KEYS = [
    ("total", "Total"),
    ("active", "Active"),
    ("pending", "Pending"),
    ("blocked", "Blocked"),
    ("cancelled", "Cancelled"),
    ("expired", "Expired"),
    ("expiringSoon", "Expiring in 7 days"),
    ("seenRecently", "Seen in 48 h"),
]


def now_millis() -> int:
    return int(datetime.now(tz=timezone.utc).timestamp() * 1000)


class MainWindow(QMainWindow):
    def __init__(self, client: ApiClient) -> None:
        super().__init__()
        self.client = client
        self.licenses: list[dict] = []
        self.selected_id: str | None = None
        self.offline = False

        self.setWindowTitle("TextPic Studio — Owner Console")
        self.resize(1360, 820)

        self.tabs = QTabWidget()
        self.tabs.addTab(self.build_customers_tab(), "Customers")
        from .releases_tab import ReleasesTab

        self.releases_tab = ReleasesTab(self.client)
        self.tabs.addTab(self.releases_tab, "Releases")
        self.tabs.addTab(self.build_settings_tab(), "Settings")
        self.tabs.currentChanged.connect(self._on_tab_changed)
        self.setCentralWidget(self.tabs)

        self.statusBar().showMessage("Ready")
        self.timer = QTimer(self)
        self.timer.setInterval(30_000)
        self.timer.timeout.connect(lambda: self.refresh(silent=True))
        self.timer.start()
        self.refresh()

    def _on_tab_changed(self, index: int) -> None:
        if self.tabs.widget(index) is getattr(self, "releases_tab", None):
            run = getattr(self.releases_tab, "refresh", None)
            if callable(run):
                run()

    # ------------------------------------------------------------------ layout
    def build_customers_tab(self) -> QWidget:
        page = QWidget()
        layout = QVBoxLayout(page)
        layout.setContentsMargins(18, 16, 18, 12)
        layout.setSpacing(12)

        header = QHBoxLayout()
        title = QLabel("Customers")
        title.setObjectName("Title")
        header.addWidget(title)
        self.connection_label = QLabel(" ")
        self.connection_label.setObjectName("Muted")
        header.addSpacing(12)
        header.addWidget(self.connection_label)
        header.addStretch(1)

        self.create_button = QPushButton("Create key")
        self.create_button.setObjectName("Primary")
        self.create_button.clicked.connect(self.create_key)
        header.addWidget(self.create_button)

        refresh = QPushButton("Refresh")
        refresh.clicked.connect(lambda: self.refresh())
        header.addWidget(refresh)

        export = QPushButton("Export CSV")
        export.clicked.connect(self.export_csv)
        header.addWidget(export)
        layout.addLayout(header)

        self.banner = QLabel("")
        self.banner.setObjectName("Banner")
        self.banner.setWordWrap(True)
        self.banner.hide()
        layout.addWidget(self.banner)

        self.stats_row = QHBoxLayout()
        self.stats_row.setSpacing(8)
        self.stat_labels: dict[str, QLabel] = {}
        for key, label in STAT_KEYS:
            self.stats_row.addWidget(self.build_stat_card(key, label))
        layout.addLayout(self.stats_row)

        filters = QHBoxLayout()
        self.search_field = QLineEdit()
        self.search_field.setPlaceholderText("Search name, key, device code or note…")
        self.search_field.setClearButtonEnabled(True)
        self.search_field.textChanged.connect(self.apply_filters)
        filters.addWidget(self.search_field, 3)

        self.status_filter = QComboBox()
        for value, label in [
            ("", "All statuses"),
            ("pending", "Pending"),
            ("active", "Active"),
            ("expired", "Expired"),
            ("blocked", "Blocked"),
            ("cancelled", "Cancelled"),
        ]:
            self.status_filter.addItem(label, value)
        self.status_filter.currentIndexChanged.connect(self.apply_filters)
        filters.addWidget(self.status_filter, 1)
        layout.addLayout(filters)

        splitter = QSplitter(Qt.Orientation.Horizontal)

        self.table = QTableWidget(0, len(COLUMNS))
        self.table.setHorizontalHeaderLabels(COLUMNS)
        self.table.verticalHeader().setVisible(False)
        self.table.setSelectionBehavior(QAbstractItemView.SelectionBehavior.SelectRows)
        self.table.setSelectionMode(QAbstractItemView.SelectionMode.SingleSelection)
        self.table.setEditTriggers(QAbstractItemView.EditTrigger.NoEditTriggers)
        self.table.setAlternatingRowColors(False)
        self.table.horizontalHeader().setSectionResizeMode(QHeaderView.ResizeMode.Interactive)
        self.table.horizontalHeader().setStretchLastSection(True)
        self.table.itemSelectionChanged.connect(self.on_selection)
        self.table.setColumnWidth(0, 150)
        self.table.setColumnWidth(1, 150)
        self.table.setColumnWidth(2, 90)
        self.table.setColumnWidth(3, 160)
        splitter.addWidget(self.table)

        self.details = self.build_details_panel()
        splitter.addWidget(self.details)
        splitter.setSizes([900, 380])
        layout.addWidget(splitter, 1)
        return page

    def build_stat_card(self, key: str, label: str) -> QFrame:
        frame = QFrame()
        frame.setObjectName("StatCard")
        box = QVBoxLayout(frame)
        box.setContentsMargins(12, 10, 12, 10)
        box.setSpacing(2)
        value = QLabel("—")
        value.setStyleSheet("font-size: 20px; font-weight: 600;")
        caption = QLabel(label)
        caption.setObjectName("Muted")
        box.addWidget(value)
        box.addWidget(caption)
        self.stat_labels[key] = value
        return frame

    def build_details_panel(self) -> QWidget:
        scroll = QScrollArea()
        scroll.setWidgetResizable(True)
        holder = QWidget()
        layout = QVBoxLayout(holder)
        layout.setContentsMargins(14, 14, 14, 14)
        layout.setSpacing(10)

        self.details_title = QLabel("Select a customer")
        self.details_title.setObjectName("Title")
        layout.addWidget(self.details_title)
        self.details_status = QLabel(" ")
        self.details_status.setObjectName("Muted")
        layout.addWidget(self.details_status)

        grid = QGridLayout()
        grid.setHorizontalSpacing(10)
        grid.setVerticalSpacing(6)
        self.detail_values: dict[str, QLabel] = {}
        rows = [
            ("key", "Key"),
            ("deviceCode", "Device Code"),
            ("phone", "Phone"),
            ("created", "Created"),
            ("activated", "Activated"),
            ("expires", "Ends on"),
            ("lastSeen", "Last seen"),
            ("plan", "Plan"),
            ("usage", "Images used"),
            ("remaining", "Remaining"),
            ("note", "Note"),
        ]
        for index, (key, label) in enumerate(rows):
            name = QLabel(label)
            name.setObjectName("Muted")
            value = QLabel("—")
            value.setTextInteractionFlags(Qt.TextInteractionFlag.TextSelectableByMouse)
            value.setWordWrap(True)
            grid.addWidget(name, index, 0, Qt.AlignmentFlag.AlignTop)
            grid.addWidget(value, index, 1)
            self.detail_values[key] = value
        layout.addLayout(grid)

        self.copy_key_button = QPushButton("Copy key")
        self.copy_key_button.clicked.connect(self.copy_selected_key)
        layout.addWidget(self.copy_key_button)

        actions = QVBoxLayout()
        actions.setSpacing(6)
        self.block_button = QPushButton("Block")
        self.block_button.clicked.connect(self.toggle_block)
        actions.addWidget(self.block_button)

        self.cancel_button = QPushButton("Cancel license")
        self.cancel_button.setObjectName("Danger")
        self.cancel_button.clicked.connect(self.cancel_license)
        actions.addWidget(self.cancel_button)

        buttons = QHBoxLayout()
        self.extend_button = QPushButton("Extend")
        self.extend_button.clicked.connect(self.extend_license)
        self.plan_button = QPushButton("Change plan")
        self.plan_button.clicked.connect(self.change_plan)
        buttons.addWidget(self.extend_button)
        buttons.addWidget(self.plan_button)
        actions.addLayout(buttons)

        buttons2 = QHBoxLayout()
        self.add_images_button = QPushButton("Add images")
        self.add_images_button.clicked.connect(self.add_images)
        self.reset_usage_button = QPushButton("Reset usage")
        self.reset_usage_button.clicked.connect(self.reset_usage)
        buttons2.addWidget(self.add_images_button)
        buttons2.addWidget(self.reset_usage_button)
        actions.addLayout(buttons2)

        buttons3 = QHBoxLayout()
        self.move_button = QPushButton("Move phone")
        self.move_button.clicked.connect(self.move_phone)
        self.edit_button = QPushButton("Edit")
        self.edit_button.clicked.connect(self.edit_license)
        buttons3.addWidget(self.move_button)
        buttons3.addWidget(self.edit_button)
        actions.addLayout(buttons3)

        self.delete_button = QPushButton("Delete cancelled license")
        self.delete_button.setObjectName("Danger")
        self.delete_button.clicked.connect(self.delete_license)
        actions.addWidget(self.delete_button)
        layout.addLayout(actions)

        events_label = QLabel("History")
        events_label.setObjectName("Section")
        layout.addWidget(events_label)
        self.events_label = QLabel("—")
        self.events_label.setObjectName("Muted")
        self.events_label.setWordWrap(True)
        self.events_label.setTextInteractionFlags(Qt.TextInteractionFlag.TextSelectableByMouse)
        layout.addWidget(self.events_label)

        layout.addStretch(1)
        scroll.setWidget(holder)
        self.set_actions_enabled(False)
        return scroll

    def build_settings_tab(self) -> QWidget:
        page = QWidget()
        layout = QVBoxLayout(page)
        layout.setContentsMargins(18, 16, 18, 16)
        layout.setSpacing(12)

        title = QLabel("Settings")
        title.setObjectName("Title")
        layout.addWidget(title)

        card = QFrame()
        card.setObjectName("Card")
        box = QVBoxLayout(card)
        box.setContentsMargins(16, 16, 16, 16)
        box.setSpacing(8)

        self.settings_url = QLabel(config.server_url() or "not set")
        self.settings_url.setTextInteractionFlags(Qt.TextInteractionFlag.TextSelectableByMouse)
        self.settings_token = QLabel(mask_key(config.load_token()) if config.load_token() else "not set")
        box.addWidget(self.label_row("Server", self.settings_url))
        box.addWidget(self.label_row("Admin token", self.settings_token))

        time_row = QHBoxLayout()
        time_caption = QLabel("Day resets at midnight in")
        time_caption.setObjectName("Muted")
        self.time_zone_combo = QComboBox()
        self.time_zone_combo.setEditable(True)
        zones = sorted(available_timezones())
        self.time_zone_combo.addItems(zones)
        time_row.addWidget(time_caption)
        time_row.addWidget(self.time_zone_combo, 1)
        box.addLayout(time_row)

        buttons = QHBoxLayout()
        change = QPushButton("Change server / token")
        change.clicked.connect(self.open_setup)
        buttons.addWidget(change)
        save_tz = QPushButton("Save time zone")
        save_tz.clicked.connect(self.save_time_zone)
        buttons.addWidget(save_tz)
        buttons.addStretch(1)
        box.addLayout(buttons)

        hint = QLabel(
            "A block takes effect at the customer's next check: usually within hours, at most "
            "7 days if their phone was offline. Usage limits are enforced on the phone and "
            "confirmed by the server."
        )
        hint.setObjectName("Muted")
        hint.setWordWrap(True)
        box.addWidget(hint)
        layout.addWidget(card)

        about = QLabel(
            "TextPic Studio Owner Console · licenses, extensions, blocks and image plans.\n"
            "Back up the Worker signing key and the admin token. Without them you cannot issue "
            "new licenses for installed apps."
        )
        about.setObjectName("Muted")
        about.setWordWrap(True)
        layout.addWidget(about)
        layout.addStretch(1)
        return page

    def label_row(self, caption: str, value: QLabel) -> QWidget:
        holder = QWidget()
        row = QHBoxLayout(holder)
        row.setContentsMargins(0, 0, 0, 0)
        name = QLabel(caption)
        name.setObjectName("Muted")
        name.setFixedWidth(110)
        row.addWidget(name)
        row.addWidget(value, 1)
        return holder

    # ------------------------------------------------------------------- data
    def refresh(self, silent: bool = False) -> None:
        QGuiApplication.setOverrideCursor(Qt.CursorShape.WaitCursor)
        try:
            self.licenses = self.client.list_licenses()
            stats = self.client.stats()
            self.offline = False
            self.show_banner("")
            for key, label in self.stat_labels.items():
                label.setText(str(stats.get(key, "—")))
            self.connection_label.setText("● Online")
            self.connection_label.setStyleSheet(f"color: {COLORS['success']};")
            self.apply_filters()
            self.statusBar().showMessage(
                f"{len(self.licenses)} licenses · updated "
                f"{datetime.now().strftime('%H:%M:%S')}"
            )
            if self.selected_id:
                self.load_details(self.selected_id, quiet=True)
        except OfflineError as error:
            self.offline = True
            self.connection_label.setText("● Offline")
            self.connection_label.setStyleSheet(f"color: {COLORS['danger']};")
            self.show_banner(
                f"{error.message} Showing the last data loaded. Actions will fail until the "
                "server is reachable."
            )
        except ApiError as error:
            self.show_banner(error.message)
            if not silent:
                QMessageBox.warning(self, "License server", error.message)
        finally:
            QGuiApplication.restoreOverrideCursor()

    def show_banner(self, text: str) -> None:
        self.banner.setText(text)
        self.banner.setVisible(bool(text))
        self.banner.setStyleSheet(
            f"color: {COLORS['warning']}; background-color: rgba(227,179,65,0.10);"
            "border-radius: 8px; padding: 8px;"
        )

    def apply_filters(self) -> None:
        search = self.search_field.text().strip().lower()
        status = self.status_filter.currentData() or ""
        rows = [
            row
            for row in self.licenses
            if (not status or row.get("status") == status)
            and (
                not search
                or search in (row.get("name") or "").lower()
                or search in (row.get("key") or "").lower()
                or search in (row.get("deviceCode") or "").lower()
                or search in (row.get("note") or "").lower()
            )
        ]
        self.fill_table(rows)

    def fill_table(self, rows: list[dict]) -> None:
        now = now_millis()
        self.table.setRowCount(len(rows))
        for index, row in enumerate(rows):
            values = [
                row.get("name") or "",
                row.get("key") or "",
                status_label(row.get("status", "")),
                row.get("deviceCode") or "",
                row.get("deviceModel") or "—",
                row.get("androidVersion") or "—",
                row.get("appVersion") or "—",
                plan_label(row),
                str(row.get("usedToday", 0)),
                str(row.get("usedTotal", 0)),
                "—" if row.get("remaining") is None else str(row.get("remaining")),
                format_ts(row.get("createdAt")),
                format_ts(row.get("activatedAt")),
                end_date_label(row),
                relative_time(row.get("lastSeenAt"), now),
                row.get("note") or "",
            ]
            for column, text in enumerate(values):
                item = QTableWidgetItem(str(text))
                if column == 0:
                    item.setData(Qt.ItemDataRole.UserRole, row.get("id"))
                if column == 2:
                    item.setForeground(QColor(status_color(row.get("status", ""))))
                self.table.setItem(index, column, item)
        if self.selected_id:
            for row_index in range(self.table.rowCount()):
                item = self.table.item(row_index, 0)
                if item and item.data(Qt.ItemDataRole.UserRole) == self.selected_id:
                    self.table.selectRow(row_index)
                    break

    def current_row(self) -> dict | None:
        return next((row for row in self.licenses if row.get("id") == self.selected_id), None)

    def on_selection(self) -> None:
        items = self.table.selectedItems()
        if not items:
            return
        license_id = self.table.item(items[0].row(), 0).data(Qt.ItemDataRole.UserRole)
        if license_id:
            self.load_details(license_id)

    def load_details(self, license_id: str, quiet: bool = False) -> None:
        self.selected_id = license_id
        try:
            detail = self.client.get_license(license_id)
        except (ApiError, OfflineError) as error:
            if not quiet:
                self.show_banner(getattr(error, "message", str(error)))
            return
        row = detail.get("license", {})
        cached = next((item for item in self.licenses if item.get("id") == license_id), row)
        cached.update(row)
        self.details_title.setText(row.get("name") or "Customer")
        self.details_status.setText(status_label(row.get("status", "")))
        self.details_status.setStyleSheet(f"color: {status_color(row.get('status', ''))};")
        self.detail_values["key"].setText(row.get("key") or "—")
        self.detail_values["deviceCode"].setText(row.get("deviceCode") or "—")
        self.detail_values["phone"].setText(row.get("deviceModel") or "—")
        self.detail_values["created"].setText(format_datetime(row.get("createdAt")))
        self.detail_values["activated"].setText(format_datetime(row.get("activatedAt")))
        self.detail_values["expires"].setText(end_date_label(row))
        self.detail_values["lastSeen"].setText(relative_time(row.get("lastSeenAt")))
        self.detail_values["plan"].setText(plan_label(row))
        self.detail_values["usage"].setText(
            f"{row.get('usedTotal', 0)} total · {row.get('usedToday', 0)} today"
        )
        self.detail_values["remaining"].setText(
            "unlimited" if row.get("remaining") is None else str(row.get("remaining"))
        )
        self.detail_values["note"].setText(row.get("note") or "—")

        events = detail.get("events") or []
        if events:
            lines = []
            for event in events[:20]:
                detail_text = f" — {event.get('detail')}" if event.get("detail") else ""
                lines.append(
                    f"{format_datetime(event.get('ts'))}  {event.get('type', '')}{detail_text}"
                )
            self.events_label.setText("\n".join(lines))
        else:
            self.events_label.setText("No history yet.")

        status = row.get("status")
        self.block_button.setText("Unblock" if status == "blocked" else "Block")
        self.block_button.setEnabled(status != "cancelled")
        self.cancel_button.setEnabled(status != "cancelled")
        self.delete_button.setEnabled(status == "cancelled")
        self.set_actions_enabled(True)

    def set_actions_enabled(self, enabled: bool) -> None:
        for widget in [
            self.copy_key_button,
            self.block_button,
            self.cancel_button,
            self.extend_button,
            self.plan_button,
            self.add_images_button,
            self.reset_usage_button,
            self.move_button,
            self.edit_button,
            self.delete_button,
        ]:
            widget.setEnabled(enabled)

    # ---------------------------------------------------------------- actions
    def create_key(self) -> None:
        dialog = CreateKeyDialog(self.client, self)
        if dialog.exec() == CreateKeyDialog.DialogCode.Accepted and dialog.created:
            KeyResultDialog(dialog.created, self).exec()
            self.refresh()

    def require_selection(self) -> dict | None:
        row = self.current_row()
        if row is None:
            self.statusBar().showMessage("Pick a customer in the list first.", 4000)
        return row

    def copy_selected_key(self) -> None:
        row = self.require_selection()
        if not row:
            return
        QGuiApplication.clipboard().setText(row.get("key", ""))
        self.statusBar().showMessage("Key copied.", 3000)

    def toggle_block(self) -> None:
        row = self.require_selection()
        if not row:
            return
        blocked = row.get("status") == "blocked"
        if not blocked:
            confirmed = QMessageBox.question(
                self,
                "Block this license?",
                f"{row.get('name')} will be locked at the next check on their phone.",
            )
            if confirmed != QMessageBox.StandardButton.Yes:
                return
        self.run(
            lambda: self.client.unblock(row["id"]) if blocked else self.client.block(row["id"]),
            "License unblocked." if blocked else "License blocked.",
        )

    def cancel_license(self) -> None:
        row = self.require_selection()
        if not row:
            return
        confirmed = QMessageBox.question(
            self,
            "Cancel permanently?",
            f"{row.get('name')} loses access for good. This cannot be undone; a new key would "
            "be needed.",
        )
        if confirmed != QMessageBox.StandardButton.Yes:
            return
        self.run(lambda: self.client.cancel(row["id"]), "License cancelled.")

    def extend_license(self) -> None:
        row = self.require_selection()
        if not row:
            return
        dialog = ExtendDialog(row, self)
        if dialog.exec() != ExtendDialog.DialogCode.Accepted:
            return
        self.run(
            lambda: self.client.extend(row["id"], days=dialog.days, lifetime=dialog.lifetime),
            "License extended.",
        )

    def change_plan(self) -> None:
        row = self.require_selection()
        if not row:
            return
        dialog = PlanDialog(row, self)
        if dialog.exec() != PlanDialog.DialogCode.Accepted:
            return
        self.run(
            lambda: self.client.set_quota(row["id"], dialog.plan_mode, dialog.plan_limit),
            "Usage plan updated.",
        )

    def add_images(self) -> None:
        row = self.require_selection()
        if not row:
            return
        dialog = AddImagesDialog(row, self)
        if dialog.exec() != AddImagesDialog.DialogCode.Accepted:
            return
        self.run(lambda: self.client.add_usage(row["id"], dialog.count), "Images added.")

    def reset_usage(self) -> None:
        row = self.require_selection()
        if not row:
            return
        choice = QMessageBox.question(
            self,
            "Reset usage",
            "Reset today's count only? Choosing No resets the whole-period total instead.",
            QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No,
        )
        scope = "today" if choice == QMessageBox.StandardButton.Yes else "total"
        self.run(lambda: self.client.reset_usage(row["id"], scope), f"Usage reset ({scope}).")

    def move_phone(self) -> None:
        row = self.require_selection()
        if not row:
            return
        dialog = MovePhoneDialog(row, self)
        if dialog.exec() != MovePhoneDialog.DialogCode.Accepted:
            return
        self.run(
            lambda: self.client.move_phone(row["id"], dialog.device_code),
            "License moved to the new phone.",
        )

    def edit_license(self) -> None:
        row = self.require_selection()
        if not row:
            return
        dialog = EditDialog(row, self)
        if dialog.exec() != EditDialog.DialogCode.Accepted:
            return
        self.run(
            lambda: self.client.update_license(row["id"], dialog.name or None, dialog.note),
            "Saved.",
        )

    def delete_license(self) -> None:
        row = self.require_selection()
        if not row:
            return
        confirmed = QMessageBox.question(
            self, "Delete this license?", "The record and its history are removed for good."
        )
        if confirmed != QMessageBox.StandardButton.Yes:
            return
        self.run(lambda: self.client.delete(row["id"]), "License deleted.", deselect=True)

    def run(self, action, success_message: str, deselect: bool = False) -> None:
        QGuiApplication.setOverrideCursor(Qt.CursorShape.WaitCursor)
        try:
            action()
        except OfflineError as error:
            QMessageBox.warning(self, "Can't reach the server", error.message)
            return
        except ApiError as error:
            QMessageBox.warning(self, "Action failed", error.message)
            return
        finally:
            QGuiApplication.restoreOverrideCursor()
        self.statusBar().showMessage(success_message, 5000)
        if deselect:
            self.selected_id = None
            self.details_title.setText("Select a customer")
            self.set_actions_enabled(False)
            for label in self.detail_values.values():
                label.setText("—")
        self.refresh()

    def export_csv(self) -> None:
        path, _ = QFileDialog.getSaveFileName(
            self,
            "Save the customer list",
            f"textpic-licenses-{datetime.now().strftime('%Y-%m-%d')}.csv",
            "CSV files (*.csv)",
        )
        if not path:
            return
        try:
            payload = self.client.export_csv()
        except (ApiError, OfflineError) as error:
            QMessageBox.warning(self, "Export failed", getattr(error, "message", str(error)))
            return
        with open(path, "wb") as handle:
            handle.write(payload)
        self.statusBar().showMessage(f"Saved {path}", 6000)

    def open_setup(self) -> None:
        dialog = SetupDialog(self)
        if dialog.exec() == SetupDialog.DialogCode.Accepted:
            self.client = ApiClient(config.server_url(), config.load_token())
            self.settings_url.setText(config.server_url() or "not set")
            self.settings_token.setText(mask_key(config.load_token()) or "not set")
            self.refresh()

    def save_time_zone(self) -> None:
        zone = self.time_zone_combo.currentText().strip()
        if not zone:
            return
        try:
            self.client.set_time_zone(zone)
        except (ApiError, OfflineError) as error:
            QMessageBox.warning(self, "Could not save", getattr(error, "message", str(error)))
            return
        config.save_quota_time_zone(zone)
        self.statusBar().showMessage(f"Day boundary set to {zone}.", 5000)
