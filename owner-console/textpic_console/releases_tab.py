"""Releases tab: publish a new APK, watch adoption, take a release down."""

from __future__ import annotations

import os

from PySide6.QtCore import Qt
from PySide6.QtWidgets import (
    QCheckBox,
    QDialog,
    QDialogButtonBox,
    QFileDialog,
    QFormLayout,
    QHBoxLayout,
    QLabel,
    QLineEdit,
    QMessageBox,
    QPlainTextEdit,
    QProgressDialog,
    QPushButton,
    QTableWidget,
    QTableWidgetItem,
    QVBoxLayout,
    QWidget,
)

from .api import ApiClient, ApiError, OfflineError
from .releases import (
    Manifest,
    adoption_text,
    chunk_ranges,
    chunk_sha256,
    read_manifest,
    status_label,
    verify_manifest,
)


class PublishDialog(QDialog):
    """Choose the APK + its manifest, write notes, decide about mandatory."""

    def __init__(self, parent: QWidget | None = None) -> None:
        super().__init__(parent)
        self.setWindowTitle("Publish a new version")
        self.setMinimumWidth(560)
        self.manifest: Manifest | None = None

        layout = QVBoxLayout(self)
        form = QFormLayout()

        apk_row = QHBoxLayout()
        self.apk_edit = QLineEdit()
        self.apk_edit.setPlaceholderText("TextPic-Studio-x.y.z-release.apk")
        browse_apk = QPushButton("Choose APK…")
        browse_apk.clicked.connect(self.pick_apk)
        apk_row.addWidget(self.apk_edit, 1)
        apk_row.addWidget(browse_apk)
        form.addRow("APK", apk_row)

        manifest_row = QHBoxLayout()
        self.manifest_edit = QLineEdit()
        self.manifest_edit.setPlaceholderText("release-manifest.json next to the APK")
        browse_manifest = QPushButton("Choose manifest…")
        browse_manifest.clicked.connect(self.pick_manifest)
        manifest_row.addWidget(self.manifest_edit, 1)
        manifest_row.addWidget(browse_manifest)
        form.addRow("Manifest", manifest_row)

        self.summary = QLabel(" ")
        self.summary.setWordWrap(True)
        form.addRow("", self.summary)

        self.notes_edit = QPlainTextEdit()
        self.notes_edit.setPlaceholderText("What changed? Customers see this in the update dialog.")
        self.notes_edit.setFixedHeight(110)
        form.addRow("Release notes", self.notes_edit)

        self.mandatory_box = QCheckBox("Mandatory update (locks Rendering until customers update)")
        form.addRow("", self.mandatory_box)

        layout.addLayout(form)
        buttons = QDialogButtonBox(QDialogButtonBox.Cancel | QDialogButtonBox.Ok)
        buttons.accepted.connect(self.validate_and_accept)
        buttons.rejected.connect(self.reject)
        layout.addWidget(buttons)

    def pick_apk(self) -> None:
        path, _ = QFileDialog.getOpenFileName(self, "Choose the release APK", "", "Android package (*.apk)")
        if not path:
            return
        self.apk_edit.setText(path)
        guess = os.path.join(os.path.dirname(path), "release-manifest.json")
        if not self.manifest_edit.text() and os.path.isfile(guess):
            self.manifest_edit.setText(guess)
        self.describe()

    def pick_manifest(self) -> None:
        path, _ = QFileDialog.getOpenFileName(
            self, "Choose release-manifest.json", "", "Manifest (*.json)"
        )
        if not path:
            return
        self.manifest_edit.setText(path)
        self.describe()

    def describe(self) -> None:
        try:
            manifest = read_manifest(self.manifest_edit.text())
            verify_manifest(manifest, self.apk_edit.text())
        except ValueError as error:
            self.summary.setText(f"⚠ {error}")
            self.manifest = None
            return
        self.manifest = manifest
        size_mb = manifest.size_bytes / (1024 * 1024)
        self.summary.setText(
            f"✓ {manifest.version_name} (code {manifest.version_code}) · {size_mb:.1f} MB · "
            f"hash matches the manifest"
        )

    def validate_and_accept(self) -> None:
        try:
            manifest = read_manifest(self.manifest_edit.text())
            verify_manifest(manifest, self.apk_edit.text())
        except ValueError as error:
            QMessageBox.warning(self, "That build cannot be published", str(error))
            return
        self.manifest = manifest
        self.accept()


class ReleasesTab(QWidget):
    def __init__(self, client: ApiClient) -> None:
        super().__init__()
        self.client = client
        self.releases: list[dict] = []
        self.adoption: dict = {}
        self.min_version_code = 0

        layout = QVBoxLayout(self)
        layout.setContentsMargins(18, 16, 18, 12)
        layout.setSpacing(12)

        header = QHBoxLayout()
        title = QLabel("Releases")
        title.setObjectName("Title")
        header.addWidget(title)
        header.addStretch(1)
        self.publish_button = QPushButton("Publish new version…")
        self.publish_button.setObjectName("Primary")
        self.publish_button.clicked.connect(self.publish_new)
        header.addWidget(self.publish_button)
        refresh = QPushButton("Refresh")
        refresh.clicked.connect(self.refresh)
        header.addWidget(refresh)
        layout.addLayout(header)

        self.hint = QLabel(
            "Customers see an update prompt in the app. Android always asks them to confirm the "
            "install. Apps already updated cannot be moved back to an older version."
        )
        self.hint.setObjectName("Muted")
        self.hint.setWordWrap(True)
        layout.addWidget(self.hint)

        self.table = QTableWidget(0, 6)
        self.table.setHorizontalHeaderLabels(
            ["Version", "Status", "Size", "Published", "Mandatory", "Adoption"]
        )
        self.table.horizontalHeader().setStretchLastSection(True)
        self.table.setSelectionBehavior(QTableWidget.SelectRows)
        self.table.setEditTriggers(QTableWidget.NoEditTriggers)
        layout.addWidget(self.table, 1)

        actions = QHBoxLayout()
        self.unpublish_button = QPushButton("Unpublish")
        self.unpublish_button.clicked.connect(self.unpublish_selected)
        self.delete_button = QPushButton("Delete draft")
        self.delete_button.clicked.connect(self.delete_selected)
        actions.addWidget(self.unpublish_button)
        actions.addWidget(self.delete_button)
        actions.addStretch(1)
        layout.addLayout(actions)

    # ------------------------------------------------------------------ data
    def refresh(self) -> None:
        try:
            payload = self.client.list_releases()
        except OfflineError as error:
            self.hint.setText(str(error))
            return
        except ApiError as error:
            self.hint.setText(str(error))
            return
        self.releases = payload.get("releases") or []
        self.adoption = payload.get("adoption") or {}
        self.min_version_code = int(payload.get("minVersionCode") or 0)
        self.fill_table()

    def fill_table(self) -> None:
        self.table.setRowCount(len(self.releases))
        for row_index, release in enumerate(self.releases):
            size_mb = int(release.get("sizeBytes") or 0) / (1024 * 1024)
            published = release.get("publishedAt")
            cells = [
                f"{release.get('versionName')} ({release.get('versionCode')})",
                status_label(str(release.get("status") or "")),
                f"{size_mb:.1f} MB",
                _date(published),
                "Yes" if release.get("mandatory") else "No",
                adoption_text(release, self.adoption),
            ]
            for column, text in enumerate(cells):
                item = QTableWidgetItem(text)
                if column == 0:
                    item.setData(Qt.UserRole, release.get("versionCode"))
                self.table.setItem(row_index, column, item)
        self.table.resizeColumnsToContents()

    def selected_version(self) -> int | None:
        rows = self.table.selectionModel().selectedRows() if self.table.selectionModel() else []
        if not rows:
            return None
        item = self.table.item(rows[0].row(), 0)
        return int(item.data(Qt.UserRole)) if item else None

    # --------------------------------------------------------------- actions
    def publish_new(self) -> None:
        dialog = PublishDialog(self)
        if dialog.exec() != QDialog.Accepted or dialog.manifest is None:
            return
        manifest = dialog.manifest
        apk_path = dialog.apk_edit.text()
        notes = dialog.notes_edit.toPlainText().strip()
        try:
            created = self.client.create_release(
                manifest.version_code,
                manifest.version_name,
                notes,
                manifest.sha256,
                manifest.size_bytes,
                mandatory=dialog.mandatory_box.isChecked(),
            )
        except (ApiError, OfflineError) as error:
            QMessageBox.warning(self, "Cannot start the release", str(error))
            return

        chunk_count = int(created.get("chunkCount") or 0)
        progress = QProgressDialog(
            "Uploading…", "Cancel", 0, max(chunk_count, 1), self
        )
        progress.setWindowTitle("Uploading the APK")
        progress.setMinimumDuration(0)
        progress.setValue(0)

        uploaded = 0
        try:
            with open(apk_path, "rb") as handle:
                for index, (offset, length) in enumerate(chunk_ranges(manifest.size_bytes)):
                    if progress.wasCanceled():
                        break
                    handle.seek(offset)
                    data = handle.read(length)
                    self.client.upload_chunk(
                        manifest.version_code, index, data, chunk_sha256(data)
                    )
                    uploaded += 1
                    progress.setValue(uploaded)
                    progress.setLabelText(f"Uploading chunk {uploaded} of {chunk_count}…")
        except (ApiError, OfflineError, OSError) as error:
            progress.close()
            QMessageBox.warning(
                self,
                "Upload stopped",
                f"{error}\n\nThe draft is kept, so you can publish it again.",
            )
            self.refresh()
            return
        progress.close()

        if uploaded < chunk_count:
            QMessageBox.information(
                self, "Upload cancelled", "The draft is kept. Publish it again to finish uploading."
            )
            self.refresh()
            return

        try:
            self.client.publish_release(manifest.version_code)
        except (ApiError, OfflineError) as error:
            QMessageBox.warning(self, "Published with a problem", str(error))
            self.refresh()
            return
        QMessageBox.information(
            self,
            "Published",
            f"{manifest.version_name} is live. Customers will see the update the next time "
            "they open the app.",
        )
        self.refresh()

    def unpublish_selected(self) -> None:
        version = self.selected_version()
        if version is None:
            QMessageBox.information(self, "Choose a release", "Select a published release first.")
            return
        answer = QMessageBox.question(
            self,
            "Unpublish this release?",
            "Phone that already updated keep their version — Android cannot go back to an "
            "older app. New update checks will stop offering this release.",
        )
        if answer != QMessageBox.Yes:
            return
        try:
            self.client.unpublish_release(version)
        except (ApiError, OfflineError) as error:
            QMessageBox.warning(self, "Could not unpublish", str(error))
            return
        self.refresh()

    def delete_selected(self) -> None:
        version = self.selected_version()
        if version is None:
            QMessageBox.information(self, "Choose a release", "Select a draft or unpublished release.")
            return
        answer = QMessageBox.question(self, "Delete this release?", "This removes the stored chunks.")
        if answer != QMessageBox.Yes:
            return
        try:
            self.client.delete_release(version)
        except (ApiError, OfflineError) as error:
            QMessageBox.warning(self, "Could not delete", str(error))
            return
        self.refresh()


def _date(value) -> str:
    if not value:
        return "—"
    from datetime import datetime

    try:
        return datetime.fromtimestamp(int(value) / 1000).strftime("%d %b %Y, %H:%M")
    except (ValueError, OSError, OverflowError):
        return "—"
