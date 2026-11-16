"""Dialogs: first-run setup, create key, key result, extend, plan, move phone, edit."""

from __future__ import annotations

from datetime import date, datetime

from PySide6.QtCore import QDate, Qt
from PySide6.QtGui import QGuiApplication
from PySide6.QtWidgets import (
    QCheckBox,
    QComboBox,
    QDateEdit,
    QDialog,
    QDialogButtonBox,
    QFormLayout,
    QFrame,
    QHBoxLayout,
    QLabel,
    QLineEdit,
    QMessageBox,
    QPlainTextEdit,
    QPushButton,
    QSpinBox,
    QVBoxLayout,
    QWidget,
)

from . import config
from .api import ApiClient, ApiError, OfflineError, normalize_device_code
from .formatting import activation_message, format_ts, plan_summary
from .theme import COLORS


def card() -> QFrame:
    frame = QFrame()
    frame.setObjectName("Card")
    return frame


class BaseDialog(QDialog):
    def __init__(self, title: str, parent: QWidget | None = None) -> None:
        super().__init__(parent)
        self.setWindowTitle(title)
        self.setMinimumWidth(460)
        self.root = QVBoxLayout(self)
        self.root.setContentsMargins(20, 20, 20, 20)
        self.root.setSpacing(12)
        heading = QLabel(title)
        heading.setObjectName("Title")
        self.root.addWidget(heading)

    def add_form(self) -> QFormLayout:
        form = QFormLayout()
        form.setSpacing(10)
        form.setLabelAlignment(Qt.AlignmentFlag.AlignLeft)
        self.root.addLayout(form)
        return form

    def add_buttons(self, ok_text: str) -> QDialogButtonBox:
        box = QDialogButtonBox()
        ok = box.addButton(ok_text, QDialogButtonBox.ButtonRole.AcceptRole)
        ok.setObjectName("Primary")
        box.addButton(QDialogButtonBox.StandardButton.Cancel)
        box.accepted.connect(self.accept)
        box.rejected.connect(self.reject)
        self.root.addWidget(box)
        self.ok_button = ok
        return box


class SetupDialog(BaseDialog):
    """Server address + admin token, checked against the server before saving."""

    def __init__(self, parent: QWidget | None = None, first_run: bool = False) -> None:
        super().__init__("Connect the console to your server", parent)
        subtitle = QLabel(
            "Paste the Worker URL wrangler printed (https://…workers.dev) and the admin "
            "token you stored as the ADMIN_TOKEN secret. The token is kept in the Windows "
            "Credential Manager, never in a file."
            if first_run
            else "Change the server address or the admin token."
        )
        subtitle.setObjectName("Subtitle")
        subtitle.setWordWrap(True)
        self.root.addWidget(subtitle)

        form = self.add_form()
        self.url_field = QLineEdit(config.server_url())
        self.url_field.setPlaceholderText("https://textpic-license.your-name.workers.dev")
        form.addRow("Server URL", self.url_field)

        self.token_field = QLineEdit(config.load_token())
        self.token_field.setEchoMode(QLineEdit.EchoMode.Password)
        self.token_field.setPlaceholderText("long random admin token")
        token_row = QHBoxLayout()
        token_row.addWidget(self.token_field)
        reveal = QPushButton("Show")
        reveal.setCheckable(True)
        reveal.toggled.connect(
            lambda checked: self.token_field.setEchoMode(
                QLineEdit.EchoMode.Normal if checked else QLineEdit.EchoMode.Password
            )
        )
        token_row.addWidget(reveal)
        holder = QWidget()
        holder.setLayout(token_row)
        form.addRow("Admin token", holder)

        self.status = QLabel(" ")
        self.status.setObjectName("Muted")
        self.status.setWordWrap(True)
        self.root.addWidget(self.status)

        test_row = QHBoxLayout()
        self.test_button = QPushButton("Test connection")
        self.test_button.clicked.connect(self.test_connection)
        test_row.addWidget(self.test_button)
        test_row.addStretch(1)
        self.root.addLayout(test_row)

        self.add_buttons("Save")
        self.ok_button.setEnabled(False)
        self.accepted.connect(self.save)

    def client(self) -> ApiClient:
        return ApiClient(self.url_field.text().strip(), self.token_field.text().strip())

    def test_connection(self) -> None:
        url = self.url_field.text().strip()
        token = self.token_field.text().strip()
        if not url.startswith("https://"):
            self.set_status("The address must start with https://", error=True)
            return
        if not token:
            self.set_status("Paste the admin token first.", error=True)
            return
        self.set_status("Testing…")
        try:
            stats = self.client().stats()
        except OfflineError as error:
            self.set_status(error.message, error=True)
            return
        except ApiError as error:
            self.set_status(error.message, error=True)
            return
        self.set_status(
            f"Connected — {stats.get('total', 0)} licenses on the server.", error=False
        )
        self.ok_button.setEnabled(True)

    def set_status(self, text: str, error: bool = False) -> None:
        self.status.setText(text)
        self.status.setStyleSheet(
            f"color: {COLORS['danger'] if error else COLORS['success']};"
        )

    def save(self) -> None:
        config.save_server_url(self.url_field.text().strip())
        if not config.save_token(self.token_field.text().strip()):
            QMessageBox.warning(
                self,
                "Token not saved",
                "Windows Credential Manager refused to store the token. You can still use "
                "the console now, but you will have to paste it after restarting.",
            )


class KeyResultDialog(BaseDialog):
    """Shows the new key with the two things the owner needs to send."""

    def __init__(self, license_row: dict, parent: QWidget | None = None) -> None:
        super().__init__("Activation Key created", parent)
        self.key = license_row.get("key", "")
        self.name = license_row.get("name", "")

        hint = QLabel(
            f"Send this to {self.name or 'the customer'}. It only works on the phone with "
            f"Device Code {license_row.get('deviceCode', '')}."
        )
        hint.setObjectName("Subtitle")
        hint.setWordWrap(True)
        self.root.addWidget(hint)

        key_field = QPlainTextEdit(self.key)
        key_field.setObjectName("KeyField")
        key_field.setReadOnly(True)
        key_field.setFixedHeight(70)
        self.root.addWidget(key_field)

        summary = QLabel(
            plan_summary(
                license_row.get("durationDays"),
                license_row.get("quotaMode", "unlimited"),
                license_row.get("quotaLimit"),
            )
        )
        summary.setObjectName("Muted")
        self.root.addWidget(summary)

        row = QHBoxLayout()
        copy_key = QPushButton("Copy key")
        copy_key.clicked.connect(self.copy_key)
        copy_message = QPushButton("Copy message to send")
        copy_message.clicked.connect(self.copy_message)
        row.addWidget(copy_key)
        row.addWidget(copy_message)
        self.root.addLayout(row)

        self.feedback = QLabel(
            "Duration starts when the customer activates. The key can be copied again later "
            "from the customer list."
        )
        self.feedback.setObjectName("Muted")
        self.feedback.setWordWrap(True)
        self.root.addWidget(self.feedback)

        buttons = QDialogButtonBox()
        close = buttons.addButton("Done", QDialogButtonBox.ButtonRole.AcceptRole)
        close.setObjectName("Primary")
        close.clicked.connect(self.accept)
        self.root.addWidget(buttons)

    def copy_key(self) -> None:
        QGuiApplication.clipboard().setText(self.key)
        self.feedback.setText("Key copied to the clipboard.")

    def copy_message(self) -> None:
        QGuiApplication.clipboard().setText(activation_message(self.key, self.name))
        self.feedback.setText("Message copied — paste it into chat with the customer.")


class CreateKeyDialog(BaseDialog):
    """The owner's main job: one person, one phone, a duration and an image plan."""

    def __init__(self, client: ApiClient, parent: QWidget | None = None) -> None:
        super().__init__("Create an Activation Key", parent)
        self.client = client
        self.created: dict | None = None

        subtitle = QLabel(
            "Ask the customer for the Device Code shown on their Activation screen, then fill "
            "this in. The key is created for that phone only."
        )
        subtitle.setObjectName("Subtitle")
        subtitle.setWordWrap(True)
        self.root.addWidget(subtitle)

        form = self.add_form()
        self.name_field = QLineEdit()
        self.name_field.setPlaceholderText("Customer name")
        self.name_field.textChanged.connect(self.update_state)
        form.addRow("Customer name", self.name_field)

        self.device_field = QLineEdit()
        self.device_field.setPlaceholderText("TP-XXXX-XXXX-XXXX-XXXX")
        self.device_field.textChanged.connect(self.update_device_hint)
        form.addRow("Device Code", self.device_field)

        self.device_hint = QLabel(" ")
        self.device_hint.setObjectName("Muted")
        self.device_hint.setWordWrap(True)
        form.addRow("", self.device_hint)

        duration_row = QHBoxLayout()
        self.lifetime_box = QCheckBox("Lifetime")
        self.lifetime_box.setChecked(True)
        self.lifetime_box.toggled.connect(self.update_state)
        self.days_spin = QSpinBox()
        self.days_spin.setRange(1, 36500)
        self.days_spin.setValue(30)
        self.days_spin.setSuffix(" days")
        self.days_spin.setEnabled(False)
        self.lifetime_box.toggled.connect(lambda on: self.days_spin.setEnabled(not on))
        self.days_spin.valueChanged.connect(self.update_summary)
        duration_row.addWidget(self.lifetime_box)
        duration_row.addWidget(self.days_spin)
        duration_row.addStretch(1)
        duration_holder = QWidget()
        duration_holder.setLayout(duration_row)
        form.addRow("Duration", duration_holder)

        plan_row = QVBoxLayout()
        self.plan_combo = QComboBox()
        self.plan_combo.addItem("Unlimited images", "unlimited")
        self.plan_combo.addItem("Limited per day", "daily")
        self.plan_combo.addItem("Limited for the whole period", "total")
        self.plan_combo.currentIndexChanged.connect(self.update_state)
        plan_row.addWidget(self.plan_combo)

        limit_row = QHBoxLayout()
        self.limit_spin = QSpinBox()
        self.limit_spin.setRange(1, 1_000_000)
        self.limit_spin.setValue(50)
        self.limit_spin.setEnabled(False)
        self.limit_spin.valueChanged.connect(self.update_summary)
        limit_row.addWidget(self.limit_spin)
        limit_row.addStretch(1)
        limit_row_widget = QWidget()
        limit_row_widget.setLayout(limit_row)
        plan_row.addWidget(limit_row_widget)
        plan_holder = QWidget()
        plan_holder.setLayout(plan_row)
        form.addRow("Usage plan", plan_holder)

        self.note_field = QLineEdit()
        self.note_field.setPlaceholderText("paid cash, friend of Ali, … (optional)")
        form.addRow("Note", self.note_field)

        self.summary = QLabel("Lifetime · unlimited images")
        self.summary.setObjectName("Muted")
        self.root.addWidget(self.summary)

        self.hint = QLabel("Duration starts when the customer activates.")
        self.hint.setObjectName("Muted")
        self.root.addWidget(self.hint)

        self.add_buttons("Create key")
        self.ok_button.setEnabled(False)
        self.name_field.setFocus()

    def update_device_hint(self) -> None:
        normalized = normalize_device_code(self.device_field.text())
        if not self.device_field.text().strip():
            self.device_hint.setText(" ")
        elif normalized is None:
            self.device_hint.setText("Not a complete Device Code yet (16 characters after TP-).")
            self.device_hint.setStyleSheet(f"color: {COLORS['danger']};")
        else:
            self.device_hint.setText(f"Looks good: {normalized}")
            self.device_hint.setStyleSheet(f"color: {COLORS['success']};")
        self.update_state()

    def update_state(self) -> None:
        is_limited = self.plan_combo.currentData() != "unlimited"
        self.limit_spin.setEnabled(is_limited)
        self.update_summary()
        valid = bool(self.name_field.text().strip()) and normalize_device_code(
            self.device_field.text()
        ) is not None
        self.ok_button.setEnabled(valid)

    def update_summary(self) -> None:
        duration = None if self.lifetime_box.isChecked() else self.days_spin.value()
        mode = self.plan_combo.currentData()
        limit = self.limit_spin.value() if mode != "unlimited" else None
        self.summary.setText(plan_summary(duration, mode, limit))

    def accept(self) -> None:
        duration = None if self.lifetime_box.isChecked() else self.days_spin.value()
        mode = self.plan_combo.currentData()
        try:
            self.created = self.client.create_license(
                name=self.name_field.text(),
                device_code=self.device_field.text(),
                duration_days=duration,
                note=self.note_field.text(),
                quota_mode=mode,
                quota_limit=self.limit_spin.value() if mode != "unlimited" else None,
            )
        except OfflineError as error:
            QMessageBox.warning(self, "Can't reach the server", error.message)
            return
        except ApiError as error:
            QMessageBox.warning(self, "Could not create the key", error.message)
            return
        super().accept()


class ExtendDialog(BaseDialog):
    def __init__(self, license_row: dict, parent: QWidget | None = None) -> None:
        super().__init__("Extend license", parent)
        self.license_row = license_row
        info = QLabel(
            f"{license_row.get('name', '')} — currently ends "
            f"{format_ts(license_row.get('expiresAt'), 'never (lifetime)')}."
        )
        info.setObjectName("Subtitle")
        info.setWordWrap(True)
        self.root.addWidget(info)

        form = self.add_form()
        self.days_spin = QSpinBox()
        self.days_spin.setRange(1, 36500)
        self.days_spin.setValue(30)
        self.days_spin.setSuffix(" days")
        form.addRow("Add days", self.days_spin)

        self.lifetime_box = QCheckBox("Make it a lifetime license")
        form.addRow("", self.lifetime_box)

        self.add_buttons("Extend")
        self.days = 30
        self.lifetime = False
        self.date_millis: int | None = None
        self.accepted.connect(self.capture)

    def capture(self) -> None:
        self.days = self.days_spin.value()
        self.lifetime = self.lifetime_box.isChecked()


class PlanDialog(BaseDialog):
    def __init__(self, license_row: dict, parent: QWidget | None = None) -> None:
        super().__init__("Change usage plan", parent)
        current = QLabel(
            f"Currently {license_row.get('plan', 'Unlimited')}. Already used "
            f"{license_row.get('usedTotal', 0)} images in total."
        )
        current.setObjectName("Subtitle")
        current.setWordWrap(True)
        self.root.addWidget(current)

        form = self.add_form()
        self.mode_combo = QComboBox()
        self.mode_combo.addItem("Unlimited images", "unlimited")
        self.mode_combo.addItem("Limited per day", "daily")
        self.mode_combo.addItem("Limited for the whole period", "total")
        index = {"unlimited": 0, "daily": 1, "total": 2}.get(license_row.get("quotaMode"), 0)
        self.mode_combo.setCurrentIndex(index)
        form.addRow("Plan", self.mode_combo)

        self.limit_spin = QSpinBox()
        self.limit_spin.setRange(1, 1_000_000)
        self.limit_spin.setValue(int(license_row.get("quotaLimit") or 100))
        form.addRow("Image limit", self.limit_spin)
        self.mode_combo.currentIndexChanged.connect(self.sync)
        self.sync()

        self.add_buttons("Save plan")
        self.plan_mode = "unlimited"
        self.plan_limit = None
        self.accepted.connect(self.capture)

    def sync(self) -> None:
        self.limit_spin.setEnabled(self.mode_combo.currentData() != "unlimited")

    def capture(self) -> None:
        self.plan_mode = self.mode_combo.currentData()
        self.plan_limit = self.limit_spin.value() if self.plan_mode != "unlimited" else None


class AddImagesDialog(BaseDialog):
    def __init__(self, license_row: dict, parent: QWidget | None = None) -> None:
        super().__init__("Add images to this license", parent)
        used = QLabel(
            f"{license_row.get('usedTotal', 0)} of {license_row.get('quotaLimit') or '∞'} used. "
            "Adding images raises the total allowance without changing the duration."
        )
        used.setObjectName("Subtitle")
        used.setWordWrap(True)
        self.root.addWidget(used)

        form = self.add_form()
        self.count_spin = QSpinBox()
        self.count_spin.setRange(-100_000, 100_000)
        self.count_spin.setValue(50)
        form.addRow("Images to add", self.count_spin)

        self.add_buttons("Add")
        self.count = 50
        self.accepted.connect(lambda: setattr(self, "count", self.count_spin.value()))


class MovePhoneDialog(BaseDialog):
    def __init__(self, license_row: dict, parent: QWidget | None = None) -> None:
        super().__init__("Move license to a new phone", parent)
        info = QLabel(
            f"{license_row.get('name', '')} is on {license_row.get('deviceCode', '')}. After the "
            "move the old phone is refused at its next check and the new phone works with the "
            "same key."
        )
        info.setObjectName("Subtitle")
        info.setWordWrap(True)
        self.root.addWidget(info)

        form = self.add_form()
        self.device_field = QLineEdit()
        self.device_field.setPlaceholderText("TP-XXXX-XXXX-XXXX-XXXX")
        form.addRow("New Device Code", self.device_field)

        self.add_buttons("Move")
        self.device_code = ""
        self.ok_button.setEnabled(False)
        self.device_field.textChanged.connect(
            lambda: self.ok_button.setEnabled(normalize_device_code(self.device_field.text()) is not None)
        )
        self.accepted.connect(lambda: setattr(self, "device_code", self.device_field.text()))


class EditDialog(BaseDialog):
    def __init__(self, license_row: dict, parent: QWidget | None = None) -> None:
        super().__init__("Edit name and note", parent)
        form = self.add_form()
        self.name_field = QLineEdit(license_row.get("name", ""))
        form.addRow("Name", self.name_field)
        self.note_field = QLineEdit(license_row.get("note") or "")
        form.addRow("Note", self.note_field)
        self.add_buttons("Save")
        self.name = self.name_field.text()
        self.note = self.note_field.text()
        self.accepted.connect(self.capture)

    def capture(self) -> None:
        self.name = self.name_field.text().strip()
        self.note = self.note_field.text().strip()


class DateExtendDialog(BaseDialog):
    """Set an exact end date (used from the details panel)."""

    def __init__(self, parent: QWidget | None = None) -> None:
        super().__init__("Set end date", parent)
        form = self.add_form()
        self.date_edit = QDateEdit(QDate.currentDate().addMonths(1))
        self.date_edit.setCalendarPopup(True)
        self.date_edit.setMinimumDate(QDate.currentDate().addDays(1))
        form.addRow("Ends on", self.date_edit)
        self.add_buttons("Save date")
        self.millis: int | None = None
        self.accepted.connect(self.capture)

    def capture(self) -> None:
        chosen: date = self.date_edit.date().toPython()
        moment = datetime(chosen.year, chosen.month, chosen.day, 12, 0)
        self.millis = int(moment.timestamp() * 1000)
