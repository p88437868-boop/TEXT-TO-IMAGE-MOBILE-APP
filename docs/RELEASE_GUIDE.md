# Releasing a new version of TextPic Studio

Plain-language steps for the owner. You need: the project folder, the keystore
(`keystore/textpic-release.jks`, alias `textpic`), and your admin token for the console.

## 1. Build the release

```bash
export JAVA_HOME="/c/Users/Admin/.jdks/jbr-21.0.11"
./gradlew test assembleRelease assembleDebug bundleRelease
```

That writes:

- `app/build/outputs/apk/release/app-release.apk`
- `app/build/outputs/bundle/release/app-release.aab`
- `dist/release-manifest.json` (the `assembleRelease` step writes it last)

Copy the artifacts into `dist/` with the version in the name, then record the hashes:

```bash
cp app/build/outputs/apk/release/app-release.apk   dist/TextPic-Studio-1.1.5-release.apk
cp app/build/outputs/bundle/release/app-release.aab dist/TextPic-Studio-1.1.5-release.aab
cp app/build/outputs/apk/debug/app-debug.apk        dist/TextPic-Studio-1.1.5-debug.apk
cd dist && sha256sum TextPic-Studio-1.1.5-*.apk TextPic-Studio-1.1.5-*.aab > SHA256SUMS-1.1.5.txt
```

Bump `releaseVersionCode` and `releaseVersionName` in `app/build.gradle.kts` **before**
building — the manifest task refuses a version code that is not higher than the last
one in `dist/release-manifest.json`.

## 2. Do the one-time server update (only once, for the updater itself)

The updater needs its two new tables. Run this once, and only after you approve it:

```bash
cd server
npm run db:migrate:remote     # adds releases, release_chunks, release_events
npx wrangler deploy
```

`npm run db:migrate:remote` runs both migrations; `0001` is idempotent, so your
customers and licenses are untouched.

## 3. Publish from the console

1. Open `TextPicOwner.exe` (or `run.bat` in `owner-console/`).
2. Go to the **Releases** tab → **Publish new version…**.
3. Choose the APK. The console fills in `release-manifest.json` from the same folder
   and checks the APK's SHA-256 against it — it refuses a mismatch.
4. Type the release notes. Customers see these words in the update dialog.
5. Tick **Mandatory update** only when older versions must stop rendering (for
   example a security fix). Their keyboard still types normally; Rendering, Insert,
   Copy, Share and Save are locked until they update.
6. **OK** uploads chunk by chunk with a progress bar, then publishes.

The newest three published releases are kept; older ones are deleted automatically.

## 4. What customers see

- The app checks once every six hours (and in **Settings → Updates**).
- **Update available**: version name, size, notes, **Update** / **Later**. "Later"
  hides it for 24 hours.
- **Update**: downloads 512 KB at a time (resumable), verifies every chunk and the
  whole file, then opens Android's install confirmation. One tap to finish.
- **Mandatory**: a blocking "Update required" notice; the keyboard and settings
  still work.

## 5. Undoing a release

**Unpublish** stops new update offers. Phones that already updated keep the new
version — Android cannot downgrade an app without reinstalling it.

## 6. Never lose the signing key

Android only accepts an update signed with the same key. If
`keystore/textpic-release.jks` is lost or changed, every customer must uninstall and
reinstall, and updates stop working. Keep a backup in two places.

## Xiaomi / Redmi (MIUI, HyperOS)

HyperOS shows its own installer screens and may ask for extra confirmations
("Install anyway", a 10-second countdown, or a Xiaomi account check). That is
normal. If the `PackageInstaller` path is refused, the app falls back to the plain
"Open installer" intent. Customers should allow **Install unknown apps → TextPic**
once.
