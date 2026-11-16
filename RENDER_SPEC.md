# RENDER_SPEC.md — Exact Image Rendering Rules (source of truth)

Ported from the proven **TextPic Studio 1.0.7 (Windows)**. These dimensions, sizes and the "fill width" fix were tested against real Messenger pastes and approved. If anything is ambiguous: **preserve the pixel behavior first**, then improve the UI. If code and this file disagree, this file wins. Do not change the model without asking.

---

## 1. The problem that was solved (do not undo)

Short messages like **"Hi"** must keep a small text size when sent to Messenger. Messenger treats the image as a **photo** and scales it. The old version padded the image with **transparent** pixels to reach 460 px wide, and Messenger flattened the transparent pixels to **white**, leaving a white bar next to the bubble.

### Final fix: opaque "Fill width"
For bubble styles, make the whole 460 px-wide canvas opaque using the **bubble fill color**, and draw the text on the left or right.
- Text size does **not** grow. Bubble height does **not** change.
- Only the bubble background extends to the image edge.
- The exported PNG has **no transparent padding**, so Messenger cannot turn it white.

Trade-off (intentional): a short bubble becomes full width. The compact transparent mode exists only as an option and shows white on photo wallpapers. Never quietly revert to transparent padding, and never make compact mode the default.

---

## 2. Golden reference profile (primary acceptance baseline)

```text
style_key:      received_dark
theme:          light
font:           Verdana
font_size:      10          (logical px)
max_width:      120         (logical px)
alignment:      left
transparent:    true        (a preference; ignored while fill_width is on)
fill_width:     true
scale:          3.0
padding_x:      8
padding_y:      5
thumbnail_fit:  off
text:           "Hi"
```

### Required output
```text
460 × 69 px
Fully opaque: every pixel alpha = 255
Fill: #303030
Text: #E4E6EB
Final font size: 30 px
Final line height: 39 px
Left padding: 24 px
Top padding: 15 px
No transparent strip on the right
No white padding anywhere
```
The exported bitmap is a **solid full-width rectangle**. The in-app preview may clip to rounded corners for chat realism, but the exported PNG must stay opaque. NEVER export transparent rounded corners.

---

## 3. Constants
```text
MAX_TEXT_LENGTH       = 2000
LINE_HEIGHT_FACTOR    = 1.3
BUBBLE_RADIUS         = 18      // preview rounding only, never in the exported PNG
MESSENGER_PHOTO_WIDTH = 460     // final pixel width, NOT dp
SUPERSAMPLE           = 3
```

---

## 4. Inputs
```text
style_key, theme ("light"|"dark"), font,
font_size      logical px, clamp 10..48
max_width      logical px
alignment      "left" | "right"
transparent    boolean
fill_width     boolean
scale          1.0..4.0   (UI options 1.0, 1.5, 2.0, 3.0)
padding_x      default 8
padding_y      default 5
thumbnail_fit  "off" | "wide" | "square"
```

### Validation
- Reject empty or whitespace-only text. Reject text longer than 2,000 characters.
- `max_width` must be at least 80.
- Clamp font size to 10..48 and scale to 1.0..4.0.
- Alignment is only `left` or `right`.

---

## 5. Rendering algorithm

```text
fontSize      = clamp(font_size, 10, 48)
scale         = clamp(scale, 1.0, 4.0)
lineHeight    = fontSize * 1.3
padX          = max(0, padding_x)
padY          = max(0, padding_y)
maxTextWidth  = max(40, max_width - 2 * padX)
```

1. Lay out the text at `fontSize` logical px.
2. Wrap at word boundaries, or anywhere if a single word is too long.
3. Preserve line breaks.
4. Use a **fixed line height** of `1.3 × fontSize`, never the font's default line height.
5. Measure the widest line.
6. Compute:
```text
textWidth     = min(maxTextWidth, ceil(widestLineWidth))
textHeight    = ceil(numberOfLines * lineHeight)
contentWidth  = textWidth + 2 * padX
contentHeight = textHeight + 2 * padY
baseWidth     = ceil(contentWidth * scale)
baseHeight    = ceil(contentHeight * scale)
renderScale   = scale * 3
hiWidth       = ceil(contentWidth * renderScale)
hiHeight      = ceil(contentHeight * renderScale)
```
7. Create the high-resolution bitmap `hiWidth × hiHeight`.
8. Fill it: **bubble style** → `preset.fill`; **plain + transparent** → transparent; **plain + opaque** → theme background.
9. Draw the text at `(padX, padY)` inside the content box with the selected alignment.
10. Draw at `renderScale`, then downscale smoothly to `baseWidth × baseHeight`.
11. Apply the Messenger fill step (below).
12. Encode as lossless PNG. Never JPEG.

### Messenger fill step
Only for bubble styles when `thumbnail_fit == "off"`:
```text
targetWidth = max(baseWidth, 460, round(max_width * scale))

if targetWidth > baseWidth:
    create targetWidth × baseHeight bitmap
    fill with preset.fill                  // opaque, NEVER white
    draw the base bitmap at:
        x = 0                          if alignment == "left"
        x = targetWidth - baseWidth    if alignment == "right"
    y = 0
else:
    keep base bitmap
```
The fill is always the **bubble fill color**, not the theme background (for `received_dark` that is `#303030`).

If `fill_width == false`:
- transparent mode keeps transparent padding (legacy, can show white in Messenger);
- opaque mode fills padding with the theme background (`#FFFFFF` for light, which is also white padding);
- neither is the default.

If `thumbnail_fit != "off"`: skip the 460 fill, then grow the height to the thumbnail ratio:
- `wide` = 1.91:1, `square` = 1.0:1
- target height = `max(height, round(width / ratio))`
- bubble background = bubble fill color; plain transparent background = transparent
- draw the image centered vertically.

---

## 6. Exact dimension checks (golden profile, text "Hi")

| Scale | Output width | Output height |
|---|---:|---:|
| 1.0 | 460 | 23 |
| 1.5 | 460 | 35 |
| 2.0 | 460 | 46 |
| 3.0 | 460 | 69 |

Width stays 460 because `max_width * scale = 120 * 3 = 360` and `targetWidth = max(baseWidth, 460, 360) = 460`.
Height: `fontSize 10 → lineHeight 13 → textHeight 13 → contentHeight 13+5+5 = 23 → baseHeight = ceil(23 * 3) = 69`.
Final text: font 30 px, line height 39 px, left padding 24 px, top padding 15 px.
A long message wraps at `maxTextWidth = 104`, so base width ≤ `(104 + 16) * 3 = 360`, then the fill extends to 460. Width stays 460 while height grows with line count.

---

## 7. Styles and colors

| Style key | Fill | Text | Notes |
|---|---|---|---|
| `sent_blue` | `#0084FF` | `#FFFFFF` | Sent, Messenger blue |
| `sent_violet` | `#8E5CF7` | `#FFFFFF` | Sent, violet |
| `sent_dark` | `#0866FF` | `#FFFFFF` | Sent, dark theme |
| `received_light` | `#E4E6EB` | `#050505` | Received, light |
| `received_dark` | `#303030` | `#E4E6EB` | Received, dark |
| `plain_light` | transparent | `#1C1E21` | Text only |
| `plain_dark` | transparent | `#E4E6EB` | Text only |

Theme backgrounds: `light = #FFFFFF`, `dark = #0B0B10`.

### Fonts (Android does not ship Verdana or Segoe UI)
Do **not** silently substitute Roboto. Bundle fonts deliberately in `core/src/main/res/font/` (check each license, prefer OFL/MIT) and show the real font name in the UI:

| Windows reference font | Android bundled choice |
|---|---|
| Segoe UI | Selawik (Segoe-compatible, MIT) |
| Arial | Arimo or Liberation Sans (Arial-metric) |
| Calibri | Carlito (Calibri-metric) |
| Verdana | **User-supplied** `Verdana.ttf` if they own a license (loaded from app storage via Settings → "Add font file"). Otherwise **DejaVu Sans**, labelled "Verdana-like (DejaVu Sans)" |
| Tahoma | User-supplied if available, otherwise labelled "Tahoma-like (Noto Sans)" |
| Georgia | Gelasio (Georgia-metric) |

Also support "Add font file (.ttf/.otf)" for any custom font. Emoji use Android's system color emoji font (`EmojiCompat` or system fallback). Pixel dimensions are exact; glyph shapes can differ slightly when the font differs, and this is acceptable only when the UI says which font is used.

---

## 8. Android implementation notes (follow these)
- Exported bitmap size comes **only** from `scale` and the formulas above. NEVER use `dp`, `sp`, or `displayMetrics.density`, and never use screen density as the render scale.
- `BubbleRenderer` lives in the pure-Kotlin/Android-library `:core` module. API: `render(request: RenderRequest): RenderResult` where `RenderResult(bitmap, meta)` and `meta` exposes: `widthPx, heightPx, finalFontPx, finalLineHeightPx, padLeftPx, padTopPx, fillColor, textColor, isOpaque, fontUsedName`. Tests assert on `meta` plus real pixels.
- Use `TextPaint` with `textSize = fontSize` (logical units) for measuring, `isLinearText = true`, subpixel positioning on, `isAntiAlias = true`. Use `StaticLayout` only to get line breaks (`getLineStart/getLineEnd`); then draw each line yourself with `canvas.drawText` so the line height is exactly `1.3 × fontSize` (baseline for line `i` = `padY + i*lineHeight + (lineHeight - (descent - ascent))/2 - ascent`). Alignment applies per line within `textWidth` (`right` → line's right edge at `padX + textWidth`).
- Draw into the hi-res bitmap with `canvas.scale(renderScale, renderScale)`, then downscale with `Bitmap.createScaledBitmap(hi, baseWidth, baseHeight, true)`, ensuring exact target dimensions. Opaque bubble fills must stay alpha 255 after scaling.
- Memory guard: if `hiWidth*hiHeight` would exceed ~64 MP, reduce supersample from 3 to 2 then 1 while keeping the final dimensions identical, and keep rendering off the main thread.
- RTL and complex scripts: use `TextDirectionHeuristics.FIRSTSTRONG_LTR`; per-line alignment still follows `alignment`.
- Preview in Compose may clip rounded corners (radius 18 logical) and show the real pixel size chip (e.g. `460 × 69`). Saved/shared/inserted PNG is always the opaque bitmap.

---

## 9. Acceptance tests (implement as unit/instrumentation tests)

**Golden test** (profile in §2, text `"Hi"`): bitmap is `460 × 69`; every pixel alpha is 255; fill is `#303030`; rightmost pixel is `#303030`; text pixels reach `#E4E6EB`; `meta.finalFontPx == 30`, `finalLineHeightPx == 39`, `padLeftPx == 24`, `padTopPx == 15`; no transparent or white strip on the right.

**Scale test** (`"Hi"`, golden profile): 1.0 → 460×23, 1.5 → 460×35, 2.0 → 460×46, 3.0 → 460×69.

**Long-text test:** wraps at `maxTextWidth`; width stays 460 for the golden profile; height grows by the fixed line height; all pixels opaque; no text clipping; 2,001 characters rejected.

**Alignment test:** `left` → text starts at left padding, fill extends right; `right` → text ends at right padding, fill extends left; no transparency in either.

**Compact-mode test:** `fill_width=false` and `transparent=true` → output includes transparent padding (legacy); it must not be the default.

**Validation tests:** empty/whitespace rejected; `max_width < 80` rejected; font size and scale clamped.

**Thumbnail test:** `wide` and `square` produce the heights from the ratio formula.

**Clipboard test:** clipboard holds image data only, no text MIME; shared URI is a readable `image/png`.

**UI test:** bubble styles show "Fill width" enabled and checked by default; "Transparent PNG" is off/disabled while Fill width is checked; turning Fill width off restores it; plain styles hide Fill width; settings survive app restart.

---

## 10. Do NOT
- Add transparent padding to a short bubble and call it fixed.
- Fill padding with white or the light theme background.
- Use `dp`/`sp`/density for exported dimensions.
- Export transparent rounded corners.
- Stretch the text when filling; only the background extends.
- Use JPEG, or put the message text on the clipboard.
- Replace the `460 / max_width * scale` target model without asking.
- Make compact transparent mode the default.
- Assume Android has Verdana or Segoe UI.
