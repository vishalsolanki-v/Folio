# Changelog

What changed in each Folio release. The newest is at the top. Each entry is also the text of its
[GitHub release](https://github.com/vishalsolanki-v/Folio/releases).

## 1.0.1 (not released yet)

A reliability update: crash fixes, and fixes for editor cases where edits could be lost or come back. No new
permissions, no network access, nothing collected. Install it over 1.0.0: your Recents, pins, folders and settings
stay.

### Fixed

- **Tall PDF pages no longer crash Folio.** A very long single page (a receipt, or a web page saved as one page)
  crashed the app in landscape. It now opens, slightly softer when zoomed.
- **Sharing text with a long title in Hindi, Chinese or another non-Latin script no longer crashes Folio.** Files
  with long non-Latin names received from WhatsApp or email now keep their private copy too, so they stay in Recents.
- **Links to files from other apps no longer crash the viewer.** Some apps send a plain file link instead of a
  shared file. Folio now opens these safely, and it never opens its own private files (such as drafts) for another
  app.
- **Discard now really discards.** After choosing Discard when leaving the editor, "You have unsaved changes from
  earlier" no longer comes back the next time you edit that file.
- **"Save copy" always saves what you typed.** If Android closed Folio in the background while the save screen was
  open, the copy could be empty or miss your edits. It now uses your latest draft.
- **Typing while a save is running is no longer lost.** Characters typed during a save now count as unsaved changes,
  so Folio asks before closing.
- **Choosing Save when leaving the editor no longer closes it later by surprise.** If that save didn't go through
  (for example, you went back to fix a JSON error), a later save keeps you in the editor.
- **Restoring a draft while the editor is still loading no longer loses it.** Restore and Discard on the "unsaved
  changes" banner wait until the editor is ready.

### Changed

- The release APK no longer carries Google's encrypted "dependency metadata" block. It was unreadable to users and
  served no purpose outside Google Play.
- The README now lists the signing-certificate fingerprint, so you can check that an update really comes from this
  project.

### For developers

- Release builds now fail if the release key (`keystore.properties`) is missing, instead of silently signing with
  the debug key. Use `-Pfolio.debugSignedRelease` for a local test build.
- New tests: PDF render size, long file names, opening `file://` links, shared text with long titles, and the editor
  draft, discard and save-copy edge cases.
- New project docs: [audit](docs/AUDIT.md), [roadmap](docs/ROADMAP.md), [test matrix](docs/TEST_MATRIX.md) and
  [release checklist](docs/RELEASE_CHECKLIST.md).

## 1.0.0 (1 November 2026)

First public release.

- Opens PDF, Word (`.docx`), Excel (`.xlsx`), CSV, PowerPoint (`.pptx`), text, Markdown, code, images, audio, video
  and ZIP files, offline.
- Edits text, Markdown, CSV, code and web pages, with drafts that survive a crash.
- Recents with search, filters, sort, pins and multi-select; folders you add yourself.
- Light, Dark and Black themes; Light, Sepia and Dark paper.
- No internet permission, no other permissions, no ads, no account, nothing collected.
