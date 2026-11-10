# Android release checklist

## Baseline

Application ID com.ganesham.app is retained for installed-data continuity. Visible name MyPropertyVisit. Source versionName 1.0 / versionCode 2; use a unique code for upload. minSdk 30 (Android 11), compile/target 36 as currently configured. Recheck Play's live requirements at submission rather than treating these values as approval.

## Open actions

- [ ] Finalize brand/legal identity, support mailbox/public policy, territories and Android pricing.
- [ ] Remove/relabel unfinished calling options; retain current offline scope.
- [ ] Add accessible privacy/support links inside the app after URLs are published.
- [ ] Confirm signing owner, protect release keystore privately, enable the intended Play signing setup and produce a signed release AAB.
- [ ] Review merged permissions, bundled dependencies, data handling and store disclosures against that exact build.
- [ ] Test capture, pause/photo/resume, compass viewport and playback on physical Android devices, including the Motorola Edge 60 Pro previously reported.
- [ ] Test single/multi/all photo/video sharing and MediaStore saves on hardware; denied camera, low storage, interruptions and export failure must preserve originals.
- [ ] Test galleries with representative volume, UI themes, rotation, accessibility and supported large screens.
- [ ] Install an update over prior app data and verify all properties, media, sessions and room assignments survive.
- [ ] Verify deletion scope and explain absence of full project backup/restore.
- [ ] Complete current Play account verification/testing requirements applicable to the actual publisher account. Do not invent a universal tester-count requirement.
- [ ] Add real screenshots, listing, content/age declarations, privacy policy and Data safety answers.
- [ ] Record source commit/tag, signed artifact, symbol/mapping outputs as applicable and test evidence; push only to the Android repository.
- [ ] Submit, inspect actual review outcome and release deliberately.

## Existing evidence

During export work, assembleDebug/assembleDebugAndroidTest/lintDebug passed. Pixel 8 emulator suite passed 11 tests, including JPEG byte preservation, published gallery entry, missing-file rejection, multi-select UI, and playable recorded MP4 after export. These are debug/emulator results, not physical-device or signed release acceptance. No new physical Android verification occurred during documentation.

## Build reference

Use the configured local JBR 21 rather than the newer Android Studio JBR that previously failed with this toolchain. ANDROID_HOME points to the installed SDK. Existing commands: ./gradlew --offline :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug. Final signing is a separate owner-controlled configuration; no secret belongs in Git.
