#!/usr/bin/env python3
"""One command from "code changed" to "every phone can update".

    python tools/release.py              # next patch version: build, dist, publish, verify
    python tools/release.py --draft      # same, but upload without publishing
    python tools/release.py --no-publish # build + copy to dist only
    python tools/release.py --skip-build # publish the version already in app/build.gradle.kts
    python tools/release.py --verify-only
    python tools/release.py --dry-run    # checks only; no server writes

The version lives in one place, `app/build.gradle.kts`. This script bumps both
numbers, builds the signed APK/AAB, writes `dist/release-manifest.json` (the
Gradle task does that and refuses to go backwards), copies the artifacts and
their hashes into `dist/`, then creates the release, uploads it in 512 KB chunks
and publishes it with the owner console's own API client and the admin token the
console already keeps in the Windows Credential Manager.

Publishing is what makes the update appear: `GET /v1/latest` starts returning the
new version, so every phone offers the update the next time the app opens, and
the console's Releases tab shows it as Published. Nothing is sent to phones
directly -- the app asks the server each time it starts.
"""

from __future__ import annotations

import argparse
import base64
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GRADLE_FILE = ROOT / "app" / "build.gradle.kts"
DIST = ROOT / "dist"
CONSOLE = ROOT / "owner-console"
PUBLIC_KEY_FILE = ROOT / "app/src/main/java/com/textpic/studio/data/license/LicenseServerPublicKey.kt"
CHUNK_SIZE = 512 * 1024
USER_AGENT = "TextPicRelease/1.0 (Windows)"  # Cloudflare 403s the default urllib agent


def say(message: str = "") -> None:
    print(message, flush=True)


def fail(message: str) -> "None":
    say(f"\nSTOPPED: {message}")
    raise SystemExit(1)


# --------------------------------------------------------------------- version
def read_version() -> tuple[int, str]:
    text = GRADLE_FILE.read_text(encoding="utf-8")
    code = re.search(r"val releaseVersionCode = (\d+)", text)
    name = re.search(r'val releaseVersionName = "([^"]+)"', text)
    if not code or not name:
        fail("Could not read releaseVersionCode / releaseVersionName from app/build.gradle.kts.")
    return int(code.group(1)), name.group(1)


def bump_version(code: int, name: str) -> tuple[int, str]:
    parts = name.split(".")
    if not parts[-1].isdigit():
        fail(f'Version "{name}" does not end in a number, so it cannot be bumped automatically.')
    parts[-1] = str(int(parts[-1]) + 1)
    return code + 1, ".".join(parts)


def write_version(code: int, name: str) -> None:
    # newline="" keeps the file's CRLF endings exactly as they were.
    with GRADLE_FILE.open("r", encoding="utf-8", newline="") as handle:
        text = handle.read()
    text = re.sub(r"val releaseVersionCode = \d+", f"val releaseVersionCode = {code}", text)
    text = re.sub(r'val releaseVersionName = "[^"]+"', f'val releaseVersionName = "{name}"', text)
    with GRADLE_FILE.open("w", encoding="utf-8", newline="") as handle:
        handle.write(text)


# ------------------------------------------------------------------------ java
def java_major(jdk: Path) -> int | None:
    java = jdk / "bin" / ("java.exe" if os.name == "nt" else "java")
    if not java.is_file():
        return None
    try:
        out = subprocess.run(
            [str(java), "-XshowSettings:properties", "-version"],
            capture_output=True, text=True, timeout=30,
        ).stderr
    except OSError:
        return None
    found = re.search(r"java\.specification\.version = (\d+)", out)
    return int(found.group(1)) if found else None


def java_home() -> str:
    """A JDK Gradle 8.9 accepts; Android Studio's newest JBR can be too new."""
    home = Path.home()
    candidates: list[Path] = []
    if os.environ.get("JAVA_HOME"):
        candidates.append(Path(os.environ["JAVA_HOME"]))
    if (home / ".jdks").is_dir():
        candidates += sorted((home / ".jdks").glob("*"), reverse=True)
    candidates += [
        home / "AppData/Local/Programs/Android Studio/jbr",
        Path("C:/Program Files/Android/Android Studio/jbr"),
    ]
    for root in (Path("C:/Program Files/Java"), Path("C:/Program Files/Eclipse Adoptium")):
        if root.is_dir():
            candidates += sorted(root.glob("*"), reverse=True)
    versions = [(candidate, java_major(candidate)) for candidate in candidates]
    for candidate, major in versions:
        if major and 17 <= major <= 21:
            return str(candidate)
    for candidate, major in versions:
        if major:
            return str(candidate)
    fail("No JDK found. Install Android Studio or set JAVA_HOME, then run again.")


# ----------------------------------------------------------------------- build
def gradle(*tasks: str) -> None:
    env = dict(os.environ, JAVA_HOME=java_home())
    wrapper = ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")
    command = [str(wrapper), *tasks]
    say(f"$ {' '.join(command)}   (JAVA_HOME={env['JAVA_HOME']})")
    result = subprocess.run(command, cwd=ROOT, env=env)
    if result.returncode != 0:
        fail(f"Gradle failed on: {' '.join(tasks)}")


def artifacts(name: str) -> dict[str, Path]:
    return {
        "apk": ROOT / "app/build/outputs/apk/release/app-release.apk",
        "aab": ROOT / "app/build/outputs/bundle/release/app-release.aab",
        "debug": ROOT / "app/build/outputs/apk/debug/app-debug.apk",
    }


def copy_to_dist(name: str) -> dict[str, Path]:
    produced = artifacts(name)
    copied = {
        "apk": DIST / f"TextPic-Studio-{name}-release.apk",
        "aab": DIST / f"TextPic-Studio-{name}-release.aab",
        "debug": DIST / f"TextPic-Studio-{name}-debug.apk",
    }
    DIST.mkdir(exist_ok=True)
    for key, target in copied.items():
        if not produced[key].is_file():
            fail(f"The build did not produce {produced[key].name}.")
        shutil.copy2(produced[key], target)
    sums = DIST / f"SHA256SUMS-{name}.txt"
    lines = [f"{sha256_of(path)} *{path.name}" for path in copied.values()]
    sums.write_text("\n".join(lines) + "\n", encoding="utf-8")
    say(f"\nCopied to dist/ and wrote {sums.name}:")
    for path in copied.values():
        say(f"  {path.name}  {path.stat().st_size:,} bytes  {sha256_of(path)}")
    return copied


def sha256_of(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


# --------------------------------------------------------------------- publish
def console_modules():
    sys.path.insert(0, str(CONSOLE))
    from textpic_console import config  # noqa: PLC0415
    from textpic_console.api import ApiClient  # noqa: PLC0415
    from textpic_console import releases  # noqa: PLC0415

    return config, ApiClient, releases


def confirm(question: str, assume_yes: bool) -> bool:
    if assume_yes:
        return True
    if not sys.stdin.isatty():
        fail("Please answer the question in a terminal, or pass --yes.")
    answer = input(f"{question} [y/N] ").strip().lower()
    return answer in {"y", "yes"}


def publish(code: int, name: str, notes: str, mandatory: bool, draft: bool, assume_yes: bool) -> None:
    config, ApiClient, releases = console_modules()
    url = config.server_url()
    token = config.load_token()
    if not url:
        fail("The console has no server URL yet. Open it once and save the server address.")
    if not token:
        fail(
            "No admin token in the Windows Credential Manager. Open the owner console, "
            "go to Settings and save the token there first."
        )

    manifest_path = DIST / "release-manifest.json"
    apk_path = DIST / f"TextPic-Studio-{name}-release.apk"
    manifest = releases.read_manifest(str(manifest_path))
    releases.verify_manifest(manifest, str(apk_path))
    if manifest.version_code != code or manifest.version_name != name:
        fail(
            f"release-manifest.json says {manifest.version_name} ({manifest.version_code}) "
            f"but this build is {name} ({code}). Run without --skip-build."
        )

    client = ApiClient(url, token)
    existing = client.list_releases().get("releases") or []
    same = next((r for r in existing if int(r.get("versionCode") or 0) == code), None)
    newest = max((int(r.get("versionCode") or 0) for r in existing), default=0)
    if newest > code:
        fail(f"The server already has versionCode {newest}, which is newer than {code}.")

    if same and same.get("status") == "published":
        if str(same.get("sha256") or "").lower() != manifest.sha256:
            fail(f"versionCode {code} is already published with a different APK. Bump the version.")
        say(f"{name} ({code}) is already published with this exact APK -- nothing to upload.")
        return

    if same:
        say(f"Resuming the existing draft for {name} ({code}).")
        chunk_count = int(same.get("chunkCount") or 0)
    else:
        created = client.create_release(
            code, name, notes, manifest.sha256, manifest.size_bytes, mandatory=mandatory
        )
        chunk_count = int(created.get("chunkCount") or 0)
        say(f"Created the release {name} ({code}): {chunk_count} chunks, {manifest.size_bytes:,} bytes.")

    ranges = releases.chunk_ranges(manifest.size_bytes)
    if len(ranges) != chunk_count:
        fail(f"The server expects {chunk_count} chunks but the APK splits into {len(ranges)}.")
    with apk_path.open("rb") as handle:
        for index, (offset, length) in enumerate(ranges):
            handle.seek(offset)
            data = handle.read(length)
            client.upload_chunk(code, index, data, releases.chunk_sha256(data))
            print(f"\r  uploaded chunk {index + 1}/{chunk_count}", end="" if index + 1 < chunk_count else "\n", flush=True)
    say("  every chunk stored with its SHA-256")

    if draft:
        say(f"\nDraft kept. Publish later with: python tools/release.py --skip-build")
        return

    if not confirm(f"Publish {name} ({code}) to every phone now?", assume_yes):
        say("Not published. The uploaded draft is kept.")
        return
    client.publish_release(code)
    say(f"Published {name} ({code}).")


# ---------------------------------------------------------------------- verify
def app_public_key() -> str | None:
    if not PUBLIC_KEY_FILE.is_file():
        return None
    found = re.search(r'ENCODED: String = "([^"]+)"', PUBLIC_KEY_FILE.read_text(encoding="utf-8"))
    return found.group(1) if found else None


def fetch(url: str, raw: bool = False):
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=30) as response:
        payload = response.read()
        return payload if raw else json.loads(payload)


def verify_live(code: int, name: str, apk_path: Path) -> None:
    config, _, _ = console_modules()
    url = config.server_url().rstrip("/")
    if not url:
        fail("The console has no server URL yet.")

    say("\nChecking what phones will see...")
    envelope = fetch(f"{url}/v1/latest?vc={code - 1}")
    payload_b64, sig_b64 = envelope.get("payload"), envelope.get("sig")
    if not payload_b64 or not sig_b64:
        fail(f"/v1/latest did not return a signed reply: {str(envelope)[:200]}")
    payload_bytes = base64.urlsafe_b64decode(payload_b64 + "===")
    body = json.loads(payload_bytes)
    if body.get("none"):
        fail("/v1/latest still says there is no release.")
    say(
        f"  server offers {body.get('versionName')} ({body.get('versionCode')}), "
        f"{body.get('sizeBytes'):,} bytes, {body.get('chunkCount')} chunks"
    )
    if int(body.get("versionCode") or 0) != code:
        fail(f"/v1/latest offers versionCode {body.get('versionCode')}, expected {code}.")

    key = app_public_key()
    verified = verify_signature(url, payload_bytes, sig_b64, key, body)
    say(f"  signature: {verified}")

    if body.get("sha256") != sha256_of(apk_path):
        fail("The live sha256 does not match the APK in dist/.")
    if int(body.get("sizeBytes") or 0) != apk_path.stat().st_size:
        fail("The live sizeBytes does not match the APK in dist/.")
    say("  sha256 and size match dist/" + apk_path.name)

    declared = body.get("chunkShas") or []
    rebuilt = bytearray()
    for index in range(int(body.get("chunkCount") or 0)):
        chunk = fetch(f"{url}/v1/download/{code}/{index}", raw=True)
        digest = hashlib.sha256(chunk).hexdigest()
        if digest != declared[index]:
            fail(f"chunk {index} does not match the hash the server signed.")
        rebuilt += chunk
    if hashlib.sha256(bytes(rebuilt)).hexdigest() != body.get("sha256"):
        fail("The downloaded chunks do not add up to the signed APK hash.")
    say(f"  all {len(declared)} chunks match, re-assembled APK has the signed sha256")
    say(f"\n{body.get('versionName')} ({body.get('versionCode')}) is live. Phones see the update "
        "the next time the app opens, and the console's Releases tab shows it as Published.")


def verify_signature(url: str, payload: bytes, sig_b64: str, key: str | None, body: dict) -> str:
    if not key:
        return "not checked (public key file missing)"
    openssl = shutil.which("openssl")
    if not openssl:
        return "not checked (openssl not installed)"
    with tempdir() as folder:
        (folder / "payload.bin").write_bytes(payload)
        (folder / "sig.der").write_bytes(base64.urlsafe_b64decode(sig_b64 + "==="))
        (folder / "pub.der").write_bytes(base64.b64decode(key))
        envext = subprocess.run(
            [openssl, "pkey", "-pubin", "-inform", "DER", "-in", str(folder / "pub.der"),
             "-out", str(folder / "pub.pem")],
            capture_output=True, text=True,
        )
        if envext.returncode != 0:
            return "NOT checked (could not read the app's public key)"
        checked = subprocess.run(
            [openssl, "dgst", "-sha256", "-verify", str(folder / "pub.pem"),
             "-signature", str(folder / "sig.der"), str(folder / "payload.bin")],
            capture_output=True, text=True,
        )
        if checked.returncode != 0:
            fail(f"The server's reply is not signed by the app's key: {checked.stderr.strip()}")
        return "valid, signed by the app's own key"


class tempdir:
    """A short-lived folder inside the project (keeps temp files off the repo)."""

    def __enter__(self) -> Path:
        folder = ROOT / ".release-check"
        folder.mkdir(exist_ok=True)
        return folder

    def __exit__(self, *_) -> None:
        shutil.rmtree(ROOT / ".release-check", ignore_errors=True)


# ------------------------------------------------------------------------ main
def main() -> int:
    parser = argparse.ArgumentParser(description="Build and publish a TextPic Studio release.")
    parser.add_argument("--draft", action="store_true", help="upload the chunks but do not publish")
    parser.add_argument("--no-publish", action="store_true", help="build and copy to dist only")
    parser.add_argument("--skip-build", action="store_true", help="publish the current version as-is")
    parser.add_argument("--verify-only", action="store_true", help="only check the live release")
    parser.add_argument("--dry-run", action="store_true", help="check everything, write nothing")
    parser.add_argument("--yes", action="store_true", help="do not ask before publishing")
    parser.add_argument("--mandatory", action="store_true", help="force every phone to update")
    parser.add_argument("--notes", default="", help="what customers see in the update dialog")
    args = parser.parse_args()

    code, name = read_version()
    say(f"Current version in app/build.gradle.kts: {name} ({code})")

    if args.verify_only:
        verify_live(code, name, DIST / f"TextPic-Studio-{name}-release.apk")
        return 0

    if not args.skip_build:
        previous_code, previous_name = code, name
        code, name = bump_version(code, name)
        say(f"Next version: {name} ({code})")
        if args.dry_run:
            say("dry run: would bump app/build.gradle.kts, build and copy to dist/, then publish "
                f"{name} to everyone" if not args.no_publish else "dry run: build only")
            return 0
        write_version(code, name)
        try:
            gradle("test", "assembleRelease", "assembleDebug", "bundleRelease")
            copy_to_dist(name)
        except SystemExit:
            # A broken build must not leave the next release with a skipped number.
            write_version(previous_code, previous_name)
            say(f"Put app/build.gradle.kts back to {previous_name} ({previous_code}).")
            raise
    else:
        for path in (
            DIST / "release-manifest.json",
            DIST / f"TextPic-Studio-{name}-release.apk",
        ):
            if not path.is_file():
                fail(f"{path.name} is missing; run without --skip-build.")
        say("Using the artifacts already in dist/.")

    if args.no_publish:
        say("\nDone. Nothing was published; the APK is waiting in dist/.")
        return 0

    if args.dry_run:
        config, ApiClient, releases = console_modules()
        manifest = releases.read_manifest(str(DIST / "release-manifest.json"))
        releases.verify_manifest(manifest, str(DIST / f"TextPic-Studio-{name}-release.apk"))
        say(
            "dry run: manifest and APK agree "
            f"({manifest.version_name} / {manifest.version_code} / {manifest.sha256[:16]}...). "
            "Nothing was sent to the server."
        )
        return 0

    notes = args.notes.strip() or f"Version {name}"
    publish(code, name, notes, args.mandatory, args.draft, args.yes)
    if not args.draft:
        verify_live(code, name, DIST / f"TextPic-Studio-{name}-release.apk")
    return 0


if __name__ == "__main__":
    sys.exit(main())
