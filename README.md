# TextPic Studio (Android)

Offline text-to-image keyboard. Typed text is never logged, there is no accessibility service,
no analytics and no third-party network library. The **only** network use is the license check
described below, which sends an activation key and basic phone information to the owner's own
server, plus the update check, which sends only the installed version number — never messages,
clipboard contents, keystrokes, history, images or contacts.

Two classes may use the network (`LicenseApi` and `UpdateApi`) and a unit test fails the build if any
other class does. The clipboard history is local, the key sounds are generated on the phone, and the
keyboard needs no network at all.

## The keyboard (version 1.1.5)

Keys are sized from the screen width like Gboard: a letter key is 0.0833 of the width (60 px on a
720 px phone), 0.115 of the width tall, rows 0.029 apart, with 0.014 side margins, so the whole
keyboard is 0.71 of the width at the normal height (0.58 – 0.85 across the five height levels). All
four pages and landscape use the same rules.

The keyboard reads the phone's **navigation bar inset** and leaves that much space below the last
row, painted in the keyboard colour, so on a three-button phone the keys sit above the back / home /
recents buttons. The navigation-bar icons follow the theme: light icons on the dark theme, dark icons
on the light theme.

**Send ➤** is the primary toolbar button (grid · Preview · Send · gear · clipboard). One tap renders
the typed text with the current style, inserts the picture, clears the typed text and then presses
send. It counts as one image and obeys the licence and update gates, exactly like rendering.

**Honest limits of Send.** Android gives a keyboard no way to press another app's blue button. TextPic
can only (a) perform the field's own *Send / Go / Done* action, or (b) send an Enter key. Some apps —
Messenger is one — ignore both while an image is attached, so you still have to tap their send button.
Choose what happens in **Settings → Keyboard → Send button → After inserting**: the app's action
(default), Enter only, or nothing. A 600 ms delay (200–1500) gives the app time to attach the picture,
and "Confirm before sending" shows a two-second *Sending… Undo* chip first. If an app refuses images
from keyboards entirely, the picture is copied to the clipboard instead and no send is attempted.

Templates now open a real picker, and a template with `{name}`, `{product}` or `{price}` asks for each
value in turn. The emoji grid scrolls with momentum, and long-pressing a hand emoji chooses a skin tone.

## Updates (version 1.1.4+)

TextPic is installed from an APK, not the Play Store, so it updates itself from inside the app.

**How it works.** When the app opens it asks the owner's server `GET /v1/latest` (throttled to once
every six hours) and sends **only its version number** — no key, no device code, nothing else. The
reply is signed with the same ECDSA key as license replies; an unsigned or tampered reply is thrown
away. Both update endpoints are public on purpose, so a phone that is not activated yet can still
update (it stays locked anyway).

**What the customer does.** They see *Update available* (version, size, notes), tap **Update**, the
app downloads the APK in 512 KB chunks (resumable), checks every chunk's SHA-256, then the whole
file's SHA-256 and size, and finally Android shows its own install confirmation. One tap finishes it.

**Honest limits.**

* Android never lets a normally installed app replace itself without the user seeing Android's
  install screen. One confirmation tap is unavoidable outside the Play Store.
* The customer must allow **Install unknown apps → TextPic** once.
* The APK must be signed with the **same release key**. Never change or lose
  `keystore/textpic-release.jks`; without it, updates stop and everyone must reinstall.
* Only the first version that contains the updater has to be installed by hand. Every later version
  arrives from inside the app.
* Xiaomi/Redmi (MIUI, HyperOS) show extra installer screens — that is normal, and the app falls
  back to "Open installer" if the tidy `PackageInstaller` path is refused.
* **Mandatory updates** lock Rendering, Insert, Copy, Share and Save until the customer updates;
  the keyboard keeps typing and settings stay reachable.

Releasing is described in [`docs/RELEASE_GUIDE.md`](docs/RELEASE_GUIDE.md).

## Licenses (version 1.1.2+)

Every customer gets their **own Activation Key**, and that key works **only on the phone it was
made for**. Keys are created by the owner on their PC, and the app checks them against the owner's
own license server (a Cloudflare Worker with a small database).

> **Old offline keys are gone.** The previous version (1.1.0/1.1.1) shipped a fully offline key
> system with a `.bat` key generator. It was removed on purpose: it could not be blocked, extended
> or counted. Keys from that build no longer work, and the tool that made them is deleted.
> Anyone with the old APK must install this build and activate with a new key.

### How a customer activates

1. Install the APK and open it. The **Activation** screen shows a **Device Code**, for example
   `TP-5RKD-8WQZ-3MNB-6YTF`, with **Copy** and **Share** buttons.
2. Send that Device Code to the owner (WhatsApp, email, SMS — the Share button opens the system
   sheet).
3. Paste the **Activation Key** the owner sends back and tap **Activate**. The screen then shows
   the licensee name, the end date (or *Lifetime*), how many images are left and **Check now**.

Before activation the keyboard still types normally — the app is never a lockout. Only **Render,
Insert, Copy image, Share image, Save to gallery and template insertion** wait for a key, and they
say "Activate TextPic to use this." Settings, About and the Activation screen stay reachable.

The Device Code is `SHA-256(ANDROID_ID + app salt)` cut to 10 bytes and written in Base32
(16 characters). Only `Settings.Secure.ANDROID_ID` is read — never IMEI, serial number, MAC address
or anything that needs a permission.

* Reinstalling the same signed APK keeps the same Device Code, so the same key keeps working.
* A **factory reset** gives the phone a new ANDROID_ID: the owner uses **Move phone** in the console
  and the same key works again.
* A key made for one phone is refused on every other phone ("This key was made for a different
  phone").

### Image plans

One key can also limit how many images a customer may make: **unlimited**, **N per day** (refills
at midnight in the owner's chosen time zone) or **N in total**. A new render counts; the live
preview, re-copying an old image and failed renders do not. When the allowance is used up, new
images are blocked with a clear sentence while typing, History and the studio keep working. The
count lives on the server, so reinstalling the app does not reset it.

### What the owner needs

| Piece | Where |
| --- | --- |
| License server | `server/` (Cloudflare Worker + D1). Setup: **docs/SETUP_SERVER.md** |
| Owner Console | `dist/owner-console/TextPicOwner.exe`. Daily use: **docs/OWNER_GUIDE.md** |
| Server address | `textpic.licenseServerUrl` in `gradle.properties`, read at build time |

**This build's server:** `https://textpic-license.textpic-license.workers.dev` (Cloudflare
Worker `textpic-license` + D1 `textpic-licenses`, region APAC). The admin token is in
`server/.secrets/admin-token.txt` and the signing key in `server/.secrets/signing-key.pem` —
both gitignored, both must be backed up. The console is already connected (URL in
`%APPDATA%\TextPicOwner\config.json`, token in the Windows Credential Manager).

The console is a Windows desktop application (Python + PySide6, packaged with PyInstaller): create
keys, see every customer, block, unblock, cancel, extend, move to a new phone, change image plans,
reset usage and export CSV. The admin token is kept in the Windows Credential Manager.

### How a check works

`POST /v1/verify` with `{key, deviceCode, deviceModel, androidVersion, appVersion, nonce,
usageCount, reportId}` — nothing else, enforced by a unit test. The reply is `{payload, sig}`:
base64url JSON signed with **ECDSA P-256 / SHA-256**. The app embeds only the public key, verifies
the signature, and checks that its own random nonce comes back — so an edited reply, a fake server
or a hosts-file trick cannot unlock anything.

* Checks happen when the app starts, when the keyboard starts (at most every 6 hours) and twice a
  day in the background (WorkManager, only when online).
* **Blocked, cancelled and expired** lock immediately at the next check.
* **Offline** keeps the last signed answer for **7 days**, then asks for an online check. A server
  error, a timeout or a 5xx **never** locks anyone.
* Moving the phone clock backwards by more than a day requires an online check.

### Honest limitations

* Android cannot stop a determined person from modifying a phone they control. This design makes
  copying a key to another phone useless and makes blocking work; it is not unbreakable.
* A block reaches a phone at its next check: within hours normally, at most 7 days if the phone
  stays offline.
* The app is installed from an APK, not the Play Store, so be honest with customers about needing
  "Install unknown apps" once.
* WorkManager (already used for retention) contributes `ACCESS_NETWORK_STATE`, `WAKE_LOCK`,
  `RECEIVE_BOOT_COMPLETED` and `FOREGROUND_SERVICE` to the merged manifest. The only permissions
  added by this project are `INTERNET` and `ACCESS_NETWORK_STATE`.
* Not verified without a real phone: the actual activation tap-through, a factory reset, and
  blocks arriving while the phone is in the background.

## Build

```
gradlew test assembleRelease assembleDebug bundleRelease
```

Outputs in `app/build/outputs/`. Copy to `dist/` as
`TextPic-Studio-<version>-release.apk`, `-release.aab`, `-debug.apk` with `SHA256SUMS-<version>.txt`.

The license server address is read from `gradle.properties`:

```
textpic.licenseServerUrl=https://textpic-license.your-name.workers.dev
```

Without it the app still runs and types, and the Activation screen explains that this build has no
server address.

## Release signing

The release APK is signed with `keystore/textpic-release.jks` (alias `textpic`). **Never change or
lose that keystore**: Android refuses updates signed with another key, and the app's own certificate
check (release builds only) treats a re-signed APK as unlicensed.

## Tests

* `:license` — pure Kotlin logic: reply signature and nonce checks, license decisions, offline
  grace, clock rollback, quota rules, request whitelist, device codes, JSON.
* `:app` — Robolectric tests for the activation service with a fake transport (signed and forged
  replies, block, expiry, offline grace, storage), the gates, the keyboard refusal and the network
  policy (only `LicenseApi` may touch the network).
* `:core` — rendering golden tests, untouched.
* `server/` — `npm test` (42 tests: verify, admin, quota, time zones).
* `owner-console/` — `python -m unittest discover -s tests` (17 tests: client, plans, formatting).

The console bundles **PySide6** (LGPL-3.0) — it is a separate program on the owner's PC, not part of
the APK; its licence text is in `owner-console/` inside the packaged folder.
