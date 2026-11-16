# Setting up your license server (once, about 20 minutes)

> **Status: already deployed for you.** This server is live at
> `https://textpic-license.textpic-license.workers.dev`
> (database `textpic-licenses`, id `98d63634-9565-433a-8099-83ff27d94a17`, region APAC,
> day boundary `America/Los_Angeles`). The app is built with that address and the
> console is already connected.
>
> Your secrets are on this PC only:
> `server/.secrets/admin-token.txt` (the console token) and
> `server/.secrets/signing-key.pem` (also held as the Worker secret). **Back both up
> now** — copy them into your password manager and onto a USB stick, then keep them
> private. The rest of this page is the same flow, for the day you move accounts,
> rebuild the Worker from scratch, or want to understand what was done.

The app asks **your** server before it unlocks. The server is a Cloudflare Worker (a
small program that runs for free) with a SQLite database (D1). You create it once;
after that you only use the Owner Console.

You need:

- a free Cloudflare account (no card required for this setup),
- Node.js on this PC (already installed here: `node --version` → v26),
- the `server/` folder from this project.

Everything below runs in a terminal **inside the `server` folder**.

---

## Step 1 — Log in to Cloudflare

```
cd server
npx wrangler login
```

A browser window opens. Sign in (or create the free account first) and press **Allow**.

Check it worked:

```
npx wrangler whoami
```

## Step 2 — Create the database

```
npx wrangler d1 create textpic-licenses
```

If this account has no `workers.dev` subdomain yet, the first deploy fails with
"You need to register a workers.dev subdomain". Open
<https://dash.cloudflare.com/?to=/:account/workers/workers-and-pages> once (that
creates one automatically), or create it with the API:

```
curl -X PUT -H "Authorization: Bearer <your wrangler oauth token>" -H "Content-Type: application/json" \
  -d '{"subdomain":"your-name"}' \
  https://api.cloudflare.com/client/v4/accounts/<account-id>/workers/subdomain
```

It prints a block like:

```
[[d1_databases]]
binding = "DB"
database_name = "textpic-licenses"
database_id = "1a2b3c4d-....-...."
```

Copy that `database_id` and paste it into `server/wrangler.toml`, replacing
`PASTE_YOUR_D1_DATABASE_ID_HERE`.

## Step 3 — Create the tables

```
npm install
npm run db:migrate:remote
```

When it asks, type `y`. You should see `success`.

## Step 4 — Create the two secrets

**4a. The admin token** (the password the console uses):

```
node -e "console.log(require('crypto').randomBytes(32).toString('base64url'))"
```

Copy the line it prints, save it in your password manager, then:

```
npx wrangler secret put ADMIN_TOKEN
```

Paste the value, press Enter.

**4b. The signing key** (signs every license answer; the app refuses answers that are
not signed with it):

This project already generated the key pair. The private half is in
`server/.dev.vars` (a local, gitignored file). Open it with Notepad and copy
everything between the two `-----BEGIN PRIVATE KEY-----` lines **including** those
two lines:

```
LICENSE_SIGNING_KEY="-----BEGIN PRIVATE KEY-----
MIGH...
-----END PRIVATE KEY-----"
```

Then:

```
npx wrangler secret put LICENSE_SIGNING_KEY
```

Paste the whole PEM block (begin line, the text, end line), press Enter.

> If `server/.dev.vars` is missing, regenerate the pair with
> `node tools/gen-server-keys.mjs --force`, then rebuild the app — the new public
> key is written into the app automatically. Keys made before this are void.

**Back up the private key now** (password manager + a USB stick). If you lose it you
cannot issue keys for the app versions already installed: you would have to publish
a new app build with a new key.

## Step 5 — Deploy

```
npx wrangler deploy
```

It prints something like:

```
https://textpic-license.your-name.workers.dev
```

Open that URL in a browser and add `/health` — you should see `{"ok":true,...}`.

## Step 6 — Put the address into the app and rebuild

Open `gradle.properties` in the project root and set:

```
textpic.licenseServerUrl=https://textpic-license.your-name.workers.dev
```

Then rebuild:

```
gradlew assembleRelease
```

The APK is in `app/build/outputs/apk/release/app-release.apk`. Install that on your
phone (it replaces the older build; your license stays stored).

> An APK built without this address shows "This build has no license server address"
> on the Activation screen. It is not broken — it just needs the URL.

## Step 7 — Connect the Owner Console

1. Open `dist\owner-console\TextPicOwner.exe` (make a desktop shortcut with
   `Create Desktop Shortcut.ps1` if you like).
2. Paste the Worker URL and the **admin token** from step 4a.
3. Press **Test connection** → then **Save**.
4. In **Settings**, set "Day resets at midnight in" to your time zone and press
   **Save time zone** (this decides when a per-day image allowance refills).

The token is stored in the Windows Credential Manager, not in a file.

## Step 8 — First license

1. Install the new APK on the phone, open it: the Activation screen shows a Device Code
   like `TP-5RKD-8WQZ-3MNB-6YTF`.
2. Send that code to yourself (Share button), open the console, press **Create key**:
   name, Device Code, duration, usage plan → **Create key**.
3. **Copy message to send** and send it to the customer. They paste the key and tap
   **Activate**.

---

## Backups (do this today)

| What | Where to keep it |
| --- | --- |
| Worker private signing key | password manager + USB stick |
| Admin token | password manager |
| License database | `npx wrangler d1 export textpic-licenses --remote --output backup.sql` (every month) |
| This project + keystore | same as before — the APK signing key must never change |

## Free limits

The Worker free plan handles 100,000 requests per day and D1's free plan is far more
than a few hundred customers need for license checks (each app check is one small
request, twice a day). Nothing here needs a paid plan. If you ever host the APK files
for in-app updates, that is the part that may need a paid storage bucket — it is not
part of this build.

## Troubleshooting

| Message | Meaning / fix |
| --- | --- |
| `Can't reach the license server` | No internet, or wrong URL. Open `…/health` in the browser. |
| `The admin token was refused` | The token in the console differs from the `ADMIN_TOKEN` secret. `npx wrangler secret put ADMIN_TOKEN` again and re-paste. |
| App says `The license server had a problem` | The Worker threw an error: `npx wrangler tail` shows the log while you retry. |
| App says `Connect to the internet to activate` | The phone has no data, or the build has no server URL. |
| Console shows `HTTP 403` | Cloudflare refuses the default Python user agent. The console sends `TextPicOwner/1.0`, so this only happens with old builds. |
| Block does not apply instantly | It applies at the phone's next check: within hours, at most 7 days if the phone was offline. |
| Console shows `Offline` | It keeps the last data it loaded; actions fail until the server answers. |
