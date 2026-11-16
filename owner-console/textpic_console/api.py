"""HTTP client for the TextPic license server.

Only the standard library is used, so the packaged app has no extra moving parts.
Every call carries the owner's admin token; the token is never written to disk by
this module (see config.py).
"""

from __future__ import annotations

import json
import socket
import urllib.error
import urllib.parse
import urllib.request

DEVICE_CODE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
TIMEOUT_SECONDS = 20
USER_AGENT = "TextPicOwner/1.0 (Windows)"


class ApiError(Exception):
    """A failed call that the UI can show as a sentence."""

    def __init__(self, message: str, status: int = 0, code: str = "") -> None:
        super().__init__(message)
        self.message = message
        self.status = status
        self.code = code


class OfflineError(ApiError):
    """The server could not be reached at all."""


def normalize_device_code(value: str) -> str | None:
    """`tp 5rkd 8wqz...` -> `TP-5RKD-8WQZ-3MNB-6YTF`, or None when incomplete."""
    cleaned = "".join(ch for ch in value.upper() if ch.isalnum())
    if cleaned.startswith("TP"):
        cleaned = cleaned[2:]
    if len(cleaned) != 16:
        return None
    if any(ch not in DEVICE_CODE_ALPHABET for ch in cleaned):
        return None
    groups = [cleaned[i : i + 4] for i in range(0, 16, 4)]
    return "TP-" + "-".join(groups)


def normalize_license_key(value: str) -> str | None:
    cleaned = "".join(ch for ch in value.upper() if ch.isalnum())
    if len(cleaned) != 16:
        return None
    if any(ch not in DEVICE_CODE_ALPHABET for ch in cleaned):
        return None
    groups = [cleaned[i : i + 4] for i in range(0, 16, 4)]
    return "-".join(groups)


class ApiClient:
    def __init__(self, base_url: str, token: str) -> None:
        self.base_url = base_url.rstrip("/")
        self.token = token

    # ---------------------------------------------------------------- plumbing
    def _request(
        self,
        method: str,
        path: str,
        body: dict | None = None,
        raw: bool = False,
        raw_body: bytes | None = None,
        extra_headers: dict | None = None,
    ):
        url = self.base_url + path
        data = None
        headers = {
            "Authorization": f"Bearer {self.token}",
            "Accept": "application/json",
            # Cloudflare answers 403 to the default Python-urllib agent.
            "User-Agent": USER_AGENT,
        }
        if body is not None:
            data = json.dumps(body).encode("utf-8")
            headers["Content-Type"] = "application/json; charset=utf-8"
        if raw_body is not None:
            data = raw_body
            headers["Content-Type"] = "application/octet-stream"
        if extra_headers:
            headers.update(extra_headers)
        request = urllib.request.Request(url, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(request, timeout=TIMEOUT_SECONDS) as response:
                payload = response.read()
                if raw:
                    return payload
                if not payload:
                    return {}
                return json.loads(payload.decode("utf-8"))
        except urllib.error.HTTPError as error:
            detail = ""
            code = ""
            try:
                parsed = json.loads(error.read().decode("utf-8"))
                detail = parsed.get("message") or parsed.get("error") or ""
                code = parsed.get("error") or ""
            except Exception:  # noqa: BLE001 - the body may not be JSON
                detail = ""
            if error.code == 401:
                raise ApiError("The admin token was refused. Check it in Settings.", 401, code) from error
            if error.code == 404:
                raise ApiError(detail or "That item no longer exists on the server.", 404, code) from error
            raise ApiError(detail or f"The server returned HTTP {error.code}.", error.code, code) from error
        except urllib.error.URLError as error:
            raise OfflineError(self._offline_message(error.reason)) from error
        except socket.timeout as error:
            raise OfflineError("The server took too long to answer. Check your internet.") from error
        except json.JSONDecodeError as error:
            raise ApiError("The server sent something unexpected. Is the URL right?") from error

    @staticmethod
    def _offline_message(reason) -> str:
        text = str(reason)
        if "Name or service not known" in text or "getaddrinfo" in text:
            return "That server address does not exist. Check the URL in Settings."
        if "certificate" in text.lower():
            return "The server certificate is not trusted. Use the https:// address wrangler printed."
        return "Can't reach the license server. Check your internet connection."

    # ------------------------------------------------------------- public API
    @staticmethod
    def login_url(base_url: str) -> str:
        return base_url

    def health(self) -> dict:
        return self._request("GET", "/health")

    def stats(self) -> dict:
        return self._request("GET", "/admin/stats")

    def list_licenses(self, search: str = "", status: str = "") -> list[dict]:
        query = urllib.parse.urlencode(
            {"search": search, "status": status} if (search or status) else {}
        )
        path = "/admin/licenses" + (f"?{query}" if query else "")
        return self._request("GET", path).get("licenses", [])

    def get_license(self, license_id: str) -> dict:
        return self._request("GET", f"/admin/licenses/{urllib.parse.quote(license_id)}")

    def create_license(
        self,
        name: str,
        device_code: str,
        duration_days: int | None,
        note: str | None,
        quota_mode: str = "unlimited",
        quota_limit: int | None = None,
    ) -> dict:
        normalized = normalize_device_code(device_code)
        if normalized is None:
            raise ApiError(
                "That is not a valid Device Code. Expected TP-XXXX-XXXX-XXXX-XXXX.", 0, "INVALID_DEVICE_CODE"
            )
        body = {
            "name": name.strip(),
            "deviceCode": normalized,
            "durationDays": duration_days,
            "note": (note or "").strip() or None,
            "quotaMode": quota_mode,
            "quotaLimit": quota_limit if quota_mode != "unlimited" else None,
        }
        return self._request("POST", "/admin/licenses", body)["license"]

    def block(self, license_id: str) -> dict:
        return self._request("POST", f"/admin/licenses/{license_id}/block")["license"]

    def unblock(self, license_id: str) -> dict:
        return self._request("POST", f"/admin/licenses/{license_id}/unblock")["license"]

    def cancel(self, license_id: str) -> dict:
        return self._request("POST", f"/admin/licenses/{license_id}/cancel")["license"]

    def move_phone(self, license_id: str, device_code: str) -> dict:
        normalized = normalize_device_code(device_code)
        if normalized is None:
            raise ApiError("That is not a valid Device Code.", 0, "INVALID_DEVICE_CODE")
        return self._request(
            "POST", f"/admin/licenses/{license_id}/move-phone", {"deviceCode": normalized}
        )["license"]

    def extend(self, license_id: str, days: int | None = None, lifetime: bool = False) -> dict:
        body = {"lifetime": True} if lifetime else {"days": days}
        return self._request("POST", f"/admin/licenses/{license_id}/extend", body)["license"]

    def set_quota(self, license_id: str, mode: str, limit: int | None) -> dict:
        body = {"mode": mode, "limit": limit if mode != "unlimited" else None}
        return self._request("POST", f"/admin/licenses/{license_id}/quota", body)["license"]

    def add_usage(self, license_id: str, count: int) -> dict:
        return self._request(
            "POST", f"/admin/licenses/{license_id}/usage/add", {"count": int(count)}
        )["license"]

    def reset_usage(self, license_id: str, scope: str) -> dict:
        return self._request(
            "POST", f"/admin/licenses/{license_id}/usage/reset", {"scope": scope}
        )["license"]

    def update_license(self, license_id: str, name: str | None, note: str | None) -> dict:
        body = {}
        if name is not None:
            body["name"] = name
        if note is not None:
            body["note"] = note
        return self._request("PATCH", f"/admin/licenses/{license_id}", body)["license"]

    def delete(self, license_id: str) -> None:
        self._request("DELETE", f"/admin/licenses/{license_id}")

    def get_settings(self) -> dict:
        return self._request("GET", "/admin/settings")

    def set_time_zone(self, time_zone: str) -> dict:
        return self._request("PUT", "/admin/settings", {"quotaTz": time_zone})

    # ---------------------------------------------------------------- releases
    def list_releases(self) -> dict:
        return self._request("GET", "/admin/releases")

    def create_release(
        self,
        version_code: int,
        version_name: str,
        notes: str,
        sha256: str,
        size_bytes: int,
        mandatory: bool = False,
    ) -> dict:
        return self._request(
            "POST",
            "/admin/releases",
            {
                "versionCode": version_code,
                "versionName": version_name,
                "notes": notes,
                "sha256": sha256,
                "sizeBytes": size_bytes,
                "mandatory": mandatory,
            },
        )

    def upload_chunk(self, version_code: int, index: int, data: bytes, sha256: str) -> dict:
        return self._request(
            "PUT",
            f"/admin/releases/{version_code}/chunk/{index}",
            raw_body=data,
            extra_headers={"X-Chunk-Sha256": sha256},
        )

    def publish_release(self, version_code: int) -> dict:
        return self._request("POST", f"/admin/releases/{version_code}/publish")

    def unpublish_release(self, version_code: int) -> dict:
        return self._request("POST", f"/admin/releases/{version_code}/unpublish")

    def delete_release(self, version_code: int) -> dict:
        return self._request("DELETE", f"/admin/releases/{version_code}")

    def export_csv(self) -> bytes:
        return self._request("GET", "/admin/export.csv", raw=True)
