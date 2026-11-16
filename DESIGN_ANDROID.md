# DESIGN_ANDROID.md — TextPic Studio (Android) Design System

Dark, calm, card-based UI derived from the StoreShop reference: near-black canvas, charcoal cards, one violet accent, white as the second chart color. Every value is a token. Define colors ONCE in `ui/theme/Color.kt` and use them only through `MaterialTheme.colorScheme` / a custom `TextPicColors` object (CompositionLocal). No hex literals in screens, widgets or the keyboard code.

## 1. Color palette

### Surfaces
| Token | Hex | Use |
|---|---|---|
| `BgCanvas` | `#0B0B10` | Screen background |
| `BgGlowStart` | `#0A1830` | Header/onboarding gradient start |
| `BgGlowEnd` | `#2A0F4D` | Header/onboarding gradient end |
| `SurfaceCard` | `#1E1E23` | Cards, sheets |
| `SurfaceCardHover` | `#26262C` | Pressed / ripple tint on cards |
| `SurfaceInput` | `#141418` | Text fields |
| `SurfaceSelected` | `#2B2B31` | Selected nav item, chip, table header |
| `SurfaceNav` | `#17171B` | Bottom navigation bar, keyboard background |
| `KeyFill` | `#26262C` | Keyboard key |
| `KeyFillSpecial` | `#323239` | Shift, backspace, symbols keys |

### Lines
| Token | Hex |
|---|---|
| `BorderSubtle` | `#2E2E35` |
| `BorderStrong` | `#44444D` |
| `BorderFocus` | `#9B6DDB` |

### Text
| Token | Hex | Use |
|---|---|---|
| `TextPrimary` | `#FFFFFF` | Titles, big numbers |
| `TextSecondary` | `#B4B4BD` | Body |
| `TextMuted` | `#80808A` | Hints, axis labels |
| `TextDisabled` | `#55555E` | Disabled |
| `TextOnAccent` | `#FFFFFF` | On violet buttons |

### Accent and semantic
| Token | Hex |
|---|---|
| `Accent` | `#9B6DDB` |
| `AccentHover` | `#AE86E6` |
| `AccentPressed` | `#8456C8` |
| `AccentSoft` | `#9B6DDB` @ 18% alpha |
| `SeriesTwo` | `#F4F4F5` |
| `SeriesThree` | `#4A4A55` |
| `Success` | `#2FA394` |
| `Warning` | `#E8B04A` |
| `Danger` | `#E5484D` |
| `Info` | `#4C8DFF` |

Map to Material3: `primary=Accent`, `onPrimary=TextOnAccent`, `background=BgCanvas`, `surface=SurfaceCard`, `surfaceVariant=SurfaceSelected`, `outline=BorderSubtle`, `error=Danger`. Force dark theme; do NOT use dynamic color (Material You) so the look stays consistent.

### Messenger-style bubble presets (renderer only)
| Key | Fill | Text |
|---|---|---|
| `SENT_BLUE` | `#0084FF` | `#FFFFFF` |
| `SENT_VIOLET` | `#8E5CF7` | `#FFFFFF` |
| `SENT_DARK` | `#0866FF` | `#FFFFFF` |
| `RECEIVED_LIGHT` | `#E4E6EB` | `#050505` |
| `RECEIVED_DARK` | `#303030` | `#E4E6EB` |
| `PLAIN_LIGHT` | transparent | `#1C1E21` |
| `PLAIN_DARK` | transparent | `#E4E6EB` |

## 2. Typography
- UI font: `Inter` (bundle as `res/font/`, Regular/Medium/SemiBold/Bold), fallback system sans.
- Scale (sp): Display 28/600, H1 20/600, H2 16/600, Body 14/400, Caption 12/400, Metric 26/700.
- Renderer fonts, sizes and exported dimensions follow `RENDER_SPEC.md` (default font Verdana / "Verdana-like (DejaVu Sans)", size 10 logical px, scale 3.0). Never substitute Roboto silently; always show the font actually used. Must render any Unicode text including RTL scripts and color emoji. Theme backgrounds for the renderer: light `#FFFFFF`, dark `#0B0B10`. The in-app UI font stays Inter.

## 3. Shape, spacing, motion
- Radius: card 16dp, button 12dp, input 12dp, chip 999dp, key 8dp, bottom sheet 24dp top corners.
- Spacing: 4 / 8 / 12 / 16 / 24 / 32dp. Screen padding 16dp, card padding 16dp, grid gap 12dp.
- Cards: `SurfaceCard`, 1dp `BorderSubtle`, no elevation shadows.
- Motion: 150 ms for press and color, 220 ms for navigation (fade + 8dp slide). Respect system "remove animations".
- Touch targets ≥ 48dp (keyboard keys ≥ 44dp tall, row height 52dp).

## 4. App layout
- Single-activity Compose app with a **bottom navigation bar** (`SurfaceNav`): **Home, Compose, History, Templates, More**. Analytics and Settings live under Home/More; Help is inside More.
- Top app bar: app logo + workspace name chip (editable name, e.g. "Banana Store"), notification-style bell is NOT needed.
- **Home:** 2×2 metric cards (Images Created, Inserted, Today, Avg Length) with teal trend chips and "+N today"; Activity bar chart card (7d/30d toggle); Recent Activity list; Style usage donut card.
- **Compose (studio):** big text field; live chat-style preview that matches the exact export (light or dark chat backdrop to simulate the destination app, rounded clip for realism only); a **size chip** showing real output pixels (e.g. `460 × 69`); style preset picker; font, size, max width, alignment, resolution (1.0 / 1.5 / 2.0 / 3.0); a **"Fill width (no white bars)"** switch (on by default for bubble styles, hidden for plain styles) and a **"Transparent PNG"** switch (disabled and shown off while Fill width is on); buttons **Render & copy**, **Share**, Save, Save as template. Controls live in a bottom sheet.
- **History:** searchable list with thumbnails, status chips, swipe actions (pin, delete), filters, CSV export.
- **Templates:** card grid, placeholders `{name}` `{product}` `{price}`.
- **Analytics:** date range chips, bar/donut/line charts, export CSV/PNG.
- **Settings:** keyboard setup status, defaults, behavior, data, about.
- **Onboarding (first launch, 3 steps with gradient header):** 1) Enable keyboard, 2) Switch to TextPic keyboard, 3) Try it in a test field. Show a green check per completed step, detect state live.

## 5. Keyboard design (the core UI)
- Height ≈ 300dp portrait (adjustable: small / normal / large), `SurfaceNav` background, keys `KeyFill`, key text `TextPrimary` 18sp, pressed `SurfaceSelected`, key preview popup on press.
- **Top toolbar row (44dp):** left: logo button (opens templates strip), center: templates chips (horizontal scroll), right: style chip showing current preset and a settings gear.
- **Preview strip (appears after Render):** shows the rendered image on a checkerboard, max 120dp tall, with buttons **Insert (✔, accent)** and **Discard (✕)**.
- **Action keys** in the bottom row: `Render` (camera-text icon, = F6), `Insert` (= F9), and a long-press on Render = "Render + Insert" one-tap.
- Rows: QWERTY, shift (caps lock on double-tap), backspace (hold repeats), symbols/numbers page, emoji page (grid, recents), space, enter.
- Layouts come from JSON in `assets/layouts/` so more languages can be added. Ship English QWERTY; renderer and field handling must be RTL-ready.
- Haptic tick on key press (toggle in Settings). Optional key sound.

## 6. Components (Compose, in `ui/components/`)
`MetricCard`, `ChartCard`, `StatusChip`, `PrimaryButton`, `GhostButton`, `IconToggle`, `StyleSelector`, `PreviewCheckerboard`, `EmptyState`, `Toast/Snackbar` (accent left bar), `StepIndicator` (onboarding), `SettingsRow`, `KeycapHint` (shows keyboard actions in help screens).

## 7. Icons
Line icons, 1.75dp stroke feel, rounded. Use Material Symbols (outlined) via `androidx.compose.material:material-icons-extended`, or custom `VectorDrawable`s in `res/drawable/`. Required: home, compose/edit, history, templates, analytics, settings, help, search, copy, image, send, trash, star, pin, download/export, refresh, keyboard, check, alert, chevron-down, arrow-right, trend-up, trend-down, more, user, camera-text (custom).

## 8. Charts
Use **Vico** (`com.patrykandpatrick.vico:compose-m3`). Bars: series 1 `Accent`, series 2 `SeriesTwo`, rounded tops, 6dp gap. Dashed horizontal grid `BorderStrong` @ 60%, no vertical grid, axis labels `TextMuted` 11sp. Donut: draw with Compose `Canvas` (`drawArc`), hole 70%, colors `Accent`, `SeriesTwo`, `SeriesThree`; total in the center. Animate once on first composition (400 ms).

## 9. Brand assets Kilo MUST generate (script or hand-written XML, all in the repo)
- **Adaptive launcher icon:** `ic_launcher_background.xml` (gradient `#9B6DDB` → `#6A3FB5`), `ic_launcher_foreground.xml` (white speech bubble with a small "T" and image-frame notch, within the 66dp safe zone), and `ic_launcher_monochrome.xml` for themed icons (Android 13+). Include legacy `mipmap-*` PNGs via `ic_launcher.xml` adaptive entries.
- `logo.svg` (source) and `ic_logo.xml` (vector) for in-app use.
- **Splash screen** via `androidx.core:core-splashscreen`: `BgCanvas` background, centered logo.
- **Keyboard icon** `ic_keyboard_subtype.xml` (shows in the system keyboard picker).
- **Play-style graphics** for the README: feature graphic 1024×500 and 3 phone screenshot mock layouts are NOT required; skip them.
- Fonts: Inter TTFs in `res/font/`.
- Notification channel icon (monochrome vector) if any notification is used (none required).

## 10. Accessibility
- Text contrast ≥ 4.5:1. All icon buttons have `contentDescription`. Keys expose labels to TalkBack. Respect font scale up to 130% without clipping in the app screens.
