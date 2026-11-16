"""The Releases tab: publish a new APK so customers can update from inside the app.

The console never trusts itself: it reads `release-manifest.json`, recomputes the
APK's SHA-256, and refuses to upload when the two disagree. Uploads go chunk by
chunk (the server allows 512 KB per request) and the server re-checks every chunk.
"""

from __future__ import annotations

import hashlib
import json
import os
from dataclasses import dataclass

CHUNK_SIZE = 512 * 1024


@dataclass
class Manifest:
    package_name: str
    version_code: int
    version_name: str
    sha256: str
    size_bytes: int
    min_sdk: int | None
    built_at: str | None


def read_manifest(path: str) -> Manifest:
    """Reads the build's manifest. Raises ValueError with a friendly message."""
    try:
        with open(path, "r", encoding="utf-8") as handle:
            data = json.load(handle)
    except FileNotFoundError as error:
        raise ValueError("That release-manifest.json does not exist.") from error
    except json.JSONDecodeError as error:
        raise ValueError("That file is not valid JSON. Pick the release-manifest.json from the build.") from error
    try:
        version_code = int(data["versionCode"])
        sha256 = str(data["sha256"]).lower()
        size_bytes = int(data["sizeBytes"])
    except (KeyError, TypeError, ValueError) as error:
        raise ValueError("The manifest is missing versionCode, sha256 or sizeBytes.") from error
    if not (len(sha256) == 64 and all(c in "0123456789abcdef" for c in sha256)):
        raise ValueError("The manifest's sha256 is not 64 lowercase hex characters.")
    return Manifest(
        package_name=str(data.get("packageName", "com.textpic.studio")),
        version_code=version_code,
        version_name=str(data.get("versionName", version_code)),
        sha256=sha256,
        size_bytes=size_bytes,
        min_sdk=data.get("minSdk"),
        built_at=data.get("builtAt"),
    )


def sha256_of_file(path: str) -> str:
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def verify_manifest(manifest: Manifest, apk_path: str) -> None:
    """Raises ValueError when the APK on disk is not the one the manifest describes."""
    if not os.path.isfile(apk_path):
        raise ValueError("That APK file does not exist.")
    actual_size = os.path.getsize(apk_path)
    if actual_size != manifest.size_bytes:
        raise ValueError(
            f"The APK is {actual_size} bytes but the manifest says {manifest.size_bytes}. "
            "Pick the APK from the same build."
        )
    actual_hash = sha256_of_file(apk_path)
    if actual_hash != manifest.sha256:
        raise ValueError(
            "The APK's SHA-256 does not match release-manifest.json. "
            "Pick the APK from the same build."
        )


def chunk_ranges(size_bytes: int) -> list[tuple[int, int]]:
    """(offset, length) for every chunk the server expects."""
    if size_bytes <= 0:
        return []
    ranges: list[tuple[int, int]] = []
    offset = 0
    while offset < size_bytes:
        length = min(CHUNK_SIZE, size_bytes - offset)
        ranges.append((offset, length))
        offset += length
    return ranges


def chunk_sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def status_label(status: str) -> str:
    return {
        "draft": "Draft",
        "published": "Published",
        "unpublished": "Unpublished",
    }.get(status, status)


def adoption_text(release: dict, adoption: dict) -> str:
    on_version = int(release.get("onVersion") or 0)
    total = int((adoption or {}).get("total") or 0)
    return f"{on_version} of {total} customers"
