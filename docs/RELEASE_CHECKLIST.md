# Folio release checklist

_The quality gate for every release. It adds checks on top of the steps in [`release.md`](release.md) (how to cut a release); it doesn't replace them. Scenario details are in [TEST_MATRIX.md](TEST_MATRIX.md), and the reasons for each check are in [AUDIT.md](AUDIT.md)._

**Rule:** a release ships only when every box is ticked, or an unticked box has a written reason in the release notes. Commands assume the repo root, a shell with `ANDROID_HOME` set, and `BT=$ANDROID_HOME/build-tools/37.0.0`.

**Expected release certificate SHA-256:**
`19bf74536960acb7421491ba83096bf9f68ac68ea7ad5547810c557c328fe218`

---

## 1. Scope and version

- [ ] Every change since the last tag is listed in `CHANGELOG.md` under the new version (DIST-4). The same text goes into the GitHub release notes.
- [ ] `versionCode` is +1 and `versionName` is bumped in `app/build.gradle.kts`.
- [ ] No new `<uses-permission>`, no new network-capable dependency, no analytics, ads or crash SDK. Check with `git diff <last-tag> -- app/src/main/AndroidManifest.xml gradle/libs.versions.toml app/build.gradle.kts`.
- [ ] If the Room `version` changed: there is a `Migration` (or `@AutoMigration`) for every step from 1, and the migration test passes (CORE-9). A schema change without one crashes every upgrading user.
- [ ] README claims still match the app: formats, "40+ languages" (TXT-20), size, version, date.

## 2. Automated checks (must all pass)

- [ ] `./gradlew testDebugUnitTest` passes (85+ tests at 1.0.0).
- [ ] `./gradlew lintDebug` has no new errors.
- [ ] `./gradlew :app:licensee` passes, and any new dependency's licence has been reviewed.
- [ ] `./gradlew connectedDebugAndroidTest` passes on an **API 37** emulator and an **API 26** emulator. These tests wipe Folio's Recents (`HomeRemoveUndoTest`), so never run them on a personal phone.
- [ ] CI is green on the release commit (once CI exists, DIST-3).

## 3. Manual device pass (about 30 minutes)

On one real phone (a low-end one if possible) and the API 26 emulator:

- [ ] **Golden set** (TEST_MATRIX section A): one file of each format opens from Files and from a share (WhatsApp, Gmail or Drive).
- [ ] **Corrupted set** (section C): broken PDF, DOCX and XLSX, random bytes and an empty file each show an error, with no crash.
- [ ] **Large set**, a quick subset (section B): a 500-page PDF, a tall-page PDF in **landscape** (FMT-1), a 6 MB log with "Load more", and a big XLSX.
- [ ] **Editor:** edit, kill the app, reopen, and check that the draft restores. Edit, Back, **Discard**, reopen, and check that no draft banner appears (FEAT-3). Save, and Save copy.
- [ ] **Rotation:** PDF, video (keeps playing, FMT-2), editor with unsaved text, Home multi-select.
- [ ] **Shares:** SEND text with a long non-Latin subject (CORE-3); SEND_MULTIPLE.
- [ ] **TalkBack smoke test:** Home, Settings switches and a PDF page are announced sensibly.
- [ ] **Dark and Black themes**, plus Sepia and Dark paper, on PDF, DOCX and Markdown.
- [ ] **No crash prompt** ("Folio closed unexpectedly") appears during the whole pass.

## 4. Build the release

- [ ] Working tree clean (`git status`), on the commit to be tagged. Untracked files under `app/src` (for example a new drawable) **are** compiled into the APK, so commit them or move them out first. `keystore.properties` and the keystore are present but not tracked by git.
- [ ] `./gradlew assembleRelease` succeeds. If it silently used the debug key, step 5 catches it (DIST-2).
- [ ] Rename the output to `Folio-<version>.apk`.

## 5. Verify the artifact (all on the renamed APK)

- [ ] **Signer:** `$BT/apksigner verify --print-certs Folio-<version>.apk`. The certificate SHA-256 equals the expected value at the top. **If it shows `CN=Android Debug`, stop.**
- [ ] **Signature schemes:** `apksigner verify --verbose` shows v2 as `true` (and v3, if it has been enabled).
- [ ] **Permissions:** `$BT/aapt2 dump permissions Folio-<version>.apk` lists only `online.vishalsolanki.folio.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. No `INTERNET`, no `ACCESS_NETWORK_STATE`, no storage permission.
- [ ] **Identity:** `$BT/aapt2 dump badging Folio-<version>.apk` shows `package: name='online.vishalsolanki.folio'` with the new `versionCode` and `versionName`, and `targetSdkVersion` as intended.
- [ ] **16 KB alignment:** `$BT/zipalign -c -P 16 -v 4 Folio-<version>.apk` ends with `Verification successful`.
- [ ] **No dependency-metadata block:** the signing-block script (TEST_MATRIX section R) prints no `0x504b4453` (DIST-1).
- [ ] **Size** is noted and compared with the last release (1.0.0 was 24.8 MB). Any large jump is explained.
- [ ] **SHA-256:** `sha256sum Folio-<version>.apk` (on Windows, `certutil -hashfile Folio-<version>.apk SHA256`), recorded for the README, the release notes and the website.

## 6. Upgrade test (must pass before publishing)

From TEST_MATRIX section M:

- [ ] On a clean emulator, install **v1.0.0** (or the previous release), then create Recents (including shared files with private copies), pins, a folder, a draft and changed settings.
- [ ] `adb install -r Folio-<version>.apk` reports `Success`, not `INSTALL_FAILED_UPDATE_INCOMPATIBLE`.
- [ ] After launching: Recents, pins, folders, the draft banner, settings and private copies are all still there, and there's no crash on the first screen.

## 7. Publish

- [ ] Tag `v<version>` on the verified commit and push the tag.
- [ ] GitHub release: attach **only** `Folio-<version>.apk`, one APK, not marked as pre-release, so Obtainium picks it up. Include the changelog, the APK SHA-256 and the certificate SHA-256 (DIST-5).
- [ ] README: version, date, size, SHA-256 and the certificate fingerprint.
- [ ] Website (`src/data/apps.ts` in the portfolio repo): `version`, `size`, `sha256`, `released` and the changelog entry. "Check for updates" compares against this `version`.
- [ ] Download the APK back from GitHub and check that its SHA-256 matches the one published.

## 8. After release

- [ ] Install from the GitHub link on a real phone over the previous version (the real update path), and open a file.
- [ ] Watch "Send feedback" emails and GitHub issues for a week. Fix crash reports in a patch release (`x.y.z+1`) using this same checklist.
- [ ] Keystore backup confirmed in both safe locations (docs/release.md). A lost key means users must uninstall to update.

---

### Not yet applicable

- **F-Droid and IzzyOnDroid:** once DIST-1 and DIST-4 are done, add a step to update `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`. F-Droid compatibility is **not** verified; see AUDIT.md, "Distribution and open source".
- **Reproducible build check:** once it's set up (DIST-5), build twice from clean clones and compare the two APKs before signing.
- **Google Play:** see the draft in `docs/release.md`.
