# Third-party notices

TextPic Studio ships **no third-party source code, fonts, dictionaries or audio**.

- **Keyboard**: written for TextPic. The layouts live in `app/src/main/assets/keyboard/*.json`;
  the drawing, touch handling, clipboard history, emoji panel and text-editing panel are our own
  code. FlorisBoard (Apache-2.0) was reviewed as a reference but **no file, function or asset was
  copied**, so there is nothing to attribute. No word list, no `wordfreq` data, no CC BY-SA data is
  bundled.
- **Key sounds**: synthesised on the phone at first use by `ime/KeySoundPlayer.kt`
  (`KeySoundSynth`). No audio files are shipped and no sound is downloaded.
- **Emoji**: the characters only, drawn by the phone's own colour emoji font. No emoji image assets.
- **Fonts**: the render engine uses the fonts already installed on the phone (Verdana and friends).
  No font file is bundled, so no font licence applies to the APK.
- **Icons**: the keyboard toolbar icons (grid, gear, clipboard, shift, backspace, globe, action) are
  drawn with `Canvas` paths in `ime/KeyboardView.kt`. No Google or Gboard artwork is used.
- **Libraries**: AndroidX/Compose, Room, DataStore, WorkManager, Vico and EmojiCompat, all under
  Apache-2.0, declared in `gradle/libs.versions.toml` and packaged by Gradle with their own licence
  files.

The license server (`server/`) and the owner console (`owner-console/`) use only the Cloudflare
runtime and the Python standard library plus PySide6 (LGPL-3.0, used unmodified as a library and
bundled by PyInstaller in `dist/owner-console/`).

If any future change adds third-party code or data, it must be Apache-2.0, MIT or BSD, listed here
with its commit hash and the files used.
