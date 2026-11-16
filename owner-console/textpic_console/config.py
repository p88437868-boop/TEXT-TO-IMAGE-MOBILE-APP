"""Configuration and secret storage.

The server URL and the day time zone live in
`%APPDATA%\\TextPicOwner\\config.json`. The admin token is kept in the Windows
Credential Manager through `keyring` and never written to that file, the
repository or the packaged exe.
"""

from __future__ import annotations

import json
import os
from pathlib import Path

APP_DIR_NAME = "TextPicOwner"
KEYRING_SERVICE = "TextPicOwner"
KEYRING_USER = "admin-token"


def app_dir() -> Path:
    base = os.environ.get("APPDATA") or str(Path.home())
    directory = Path(base) / APP_DIR_NAME
    directory.mkdir(parents=True, exist_ok=True)
    return directory


def config_path() -> Path:
    return app_dir() / "config.json"


def load_config() -> dict:
    path = config_path()
    if not path.is_file():
        return {}
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, OSError):
        return {}


def save_config(data: dict) -> None:
    path = config_path()
    path.write_text(json.dumps(data, indent=2), encoding="utf-8")


def server_url() -> str:
    return (load_config().get("serverUrl") or "").strip()


def save_server_url(url: str) -> None:
    data = load_config()
    data["serverUrl"] = url.strip().rstrip("/")
    save_config(data)


def quota_time_zone() -> str:
    return load_config().get("quotaTz") or ""


def save_quota_time_zone(zone: str) -> None:
    data = load_config()
    data["quotaTz"] = zone
    save_config(data)


def load_token() -> str:
    try:
        import keyring

        return keyring.get_password(KEYRING_SERVICE, KEYRING_USER) or ""
    except Exception:  # noqa: BLE001 - a broken keyring must not crash the app
        return ""


def save_token(token: str) -> bool:
    try:
        import keyring

        keyring.set_password(KEYRING_SERVICE, KEYRING_USER, token)
        return True
    except Exception:  # noqa: BLE001
        return False


def clear_token() -> None:
    try:
        import keyring

        keyring.delete_password(KEYRING_SERVICE, KEYRING_USER)
    except Exception:  # noqa: BLE001
        pass


def is_configured() -> bool:
    return bool(server_url()) and bool(load_token())
