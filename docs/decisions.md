# Decisions and deviations from plan.md

One line each: what, why.

- M0: AGP 9 built-in Kotlin is used (no `org.jetbrains.kotlin.android` plugin applied; it is declared `apply false` in the root only to pin the Kotlin version), because AGP 9 rejects applying it by default.
- M0: compileSdk/targetSdk 37 (latest stable platform in the SDK on 2026-10-27).
- M0: Toolchain lives on D: (`D:\Android\{Studio,Sdk,.gradle,.android}`, Studio config in `D:\Android\StudioData`); JDK = Android Studio's bundled JBR.
- M1: pdfiumandroid pinned to 2.0.3 (last stable; 3.x is alpha). Its arm64/x86_64 `.so` files are 16 KB aligned (32-bit ones aren't, which Play doesn't require); verified with zipalign -P 16 and ELF headers, and the PDF test passes on the 16 KB-page API 37 emulator. No PdfRenderer fallback needed.
- M1: mammoth failed on Android (Android's SAX factory rejects `disallow-doctype-decl`; Android also ignores the `javax.xml.parsers.SAXParserFactory` system property, so a factory shim can't work). Fix: a modified copy of mammoth's 60-line `SimpleSax` in `app/src/main/java/org/zwobble/...` that rejects any DOCTYPE via a LexicalHandler, plus a Gradle artifact transform (`StripMammothSimpleSax` in app/build.gradle.kts) that removes the original class from the mammoth jar. When upgrading mammoth, diff upstream SimpleSax.java against our copy. The in-house DOCX walker fallback is no longer needed for M10, and M10's "<!DOCTYPE" pre-scan is unnecessary (covered by test `mammoth_doesNotResolveExternalEntities`).
- M1: Espresso pinned to 3.7.0 for androidTest; the 3.5.0 pulled in by compose-ui-test crashes on API 37 (`InputManager.getInstance` removed).
- M1: Release APK permissions: only `WAKE_LOCK` (from Media3, normal permission, no prompt) and androidx's internal receiver permission. No network or storage permissions.
- M2: Fonts are the variable Inter (opsz + wght) and JetBrains Mono (wght) from google/fonts, bundled in res/font (~1 MB). Inter is used at optical size 14 for text and 32 for headlines (`InterText` / `InterDisplay`). OFL license texts are in assets/licenses for the M5 licenses screen.
- M2: 38 Material Symbols (weight 300) converted from SVG to vector drawables by a small script; push_pin only exists in the older 24-unit format, so it uses a 24 viewport.
- M2: The design catalog is a debug-only launcher activity (`src/debug`, "Folio catalog"; `adb shell am start -n com.folio.app/.debug.DesignCatalogActivity --es theme Dark`). M5 should also link it from Settings in debug builds.
- M2: FindBar has `autoFocus` (default true) so the viewer focuses it immediately; the catalog turns it off.
- M3: `TextCodec` does not normalise line endings (plan §4.5 said normalise to `\n`). Text keeps its original separators, so an unedited decode→encode is byte-identical even for mixed endings; `lineSeparator` is just the default for new lines. M7 must confirm Sora keeps per-line separators (if not, normalise only when the file has a single separator style). `TextDoc` has no `endsWithNewline` (the text itself carries it).
- M3: `MimeSniffer.detect` owns the whole priority order (magic > ZIP peek > extension > intent MIME > text heuristic); `FileResolver` just calls it. A truncated or unreadable ZIP falls back to a ZIP-based extension (docx, xlsx...) when present. Generic MIME types (text/plain, octet-stream) never override a known extension.
- M3: `FileSaver` keeps new content in memory (editing is capped at 5 MB) instead of a `.new` file. It refuses to overwrite when the original is over 50 MB (it can't back it up), and keeps the `.bak` if restoring fails, so the original is never lost. Tested with failing fakes and on a real FileProvider with mode "wt".
- M3: `ZipSafety.Budget` is `startEntry(compressedSize)` + `written(chunk)` so it can stop a bomb mid-write.
- M3: vcf and ics map to the TEXT family (readable as text) until their v1.1 viewers; other v1.1 formats map to UNKNOWN with proper names and badges.
- M3: `UriAccess.canWrite` reads `Document.COLUMN_FLAGS` directly instead of adding androidx.documentfile.
- M3: The mammoth transform is not requested on unit-test classpaths (it dropped the app's own classes there); JVM tests don't need it.
