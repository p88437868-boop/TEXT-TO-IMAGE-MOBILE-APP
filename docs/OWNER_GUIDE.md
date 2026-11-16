# Owner guide — licenses, limits, blocks, new phones

Open **TextPic Owner** (desktop shortcut). Top of the window: how many customers you
have in each state, and whether the console can reach the server.

## Giving someone the app

1. Install `TextPic-Studio-<version>-release.apk` on their phone.
2. They open it: the Activation screen shows **their Device Code** (`TP-…`) with a
   Copy and a Share button. They send it to you.
3. In the console press **Create key**:
   - **Customer name** — shown in their app and in your list.
   - **Device Code** — paste it; the field turns green when it is complete.
   - **Duration** — Lifetime, or a number of days. The clock starts **when they
     activate**, not when you create the key.
   - **Usage plan** — Unlimited, limited per day, or limited for the whole period.
     The line under the fields repeats your choice in plain words.
   - **Note** — optional ("paid cash", "friend of Ali").
4. Press **Create key**. The key appears; **Copy message to send** puts a ready
   message on the clipboard. Send it.
5. They paste the key and tap **Activate**. The app shows their name and end date.

One key only works on the phone whose code you used. On another phone the app says
*"This key was made for a different phone."*

## The customer list

Search by name, key, device code or note; filter by status. Each row shows the plan,
how many images were used today and in total, what is left, the end date and when the
phone last checked in. Click a row for the full details and the history (created,
activated, blocked, extended, moved, wrong-phone attempts).

## Everyday actions

| Want to | Do this | What the customer sees |
| --- | --- | --- |
| Give more time | **Extend** → days (or lifetime) | Nothing until they open the app; then the new end date |
| Add images | **Add images** → count | Their remaining count goes up |
| Change the plan | **Change plan** | New limits from the next check |
| Let someone try less | **Block** | Locked at the next check, message "This license was blocked" |
| Undo a block | **Unblock** | Works again immediately |
| End it for good | **Cancel license** | Locked at the next check, cannot be undone |
| New phone, same person | **Move phone** → their new code | Old phone refused, new phone works with the same key |
| Fix a typo | **Edit** | The new name appears in their app |
| Clean up | **Delete cancelled license** | Removed from your list (only after cancel) |
| Keep a copy | **Export CSV** | A spreadsheet of everything |

**Timing:** a block, extension or move reaches the phone at its next check — when the
app is opened, when the keyboard starts (at most every 6 hours) or with the twice-a-day
background check. A phone that stays offline is locked by its own copy of the last
answer after 7 days.

## Image limits in practice

- One image is counted each time a **new** image is rendered: Render in the keyboard,
  Render & copy / Share / Save in the studio, and renders from shared text.
- The live preview, re-copying or re-sharing an old image from History, and failed
  renders are **not** counted.
- A per-day plan refills at midnight in the time zone you set in **Settings**.
- Reinstalling the app does not reset usage: the server keeps the totals.
- Typing is never limited, and the keyboard never stops working because of a limit.

## Housekeeping

- **Back up** the Worker signing key (password manager + USB stick) and the admin
  token. Without the signing key you cannot issue keys for installed apps.
- Export the database occasionally:
  `npx wrangler d1 export textpic-licenses --remote --output backup.sql`
- Every action is written to each license's history, so you can see what you did and
  when.
- The console never stores the admin token in a file; it uses the Windows Credential
  Manager.

## When something looks wrong

- **Console says Offline** — no internet, or the Worker is down. It keeps showing the
  last data; nothing is lost.
- **"This phone already has a license"** — that phone already has a live license.
  Use **Extend** on the existing one instead (search the device code).
- **Customer sees "Connect to the internet to check your license"** — their phone has
  been offline for more than 7 days. They open the app once with data and it clears.
- **Customer sees "Your phone's date is set backwards"** — set the date to automatic
  and open the app once online.
