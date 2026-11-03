# PropertyLens for Android

Independent native Kotlin Android app. Repository: https://github.com/1Bullzeye1/PropertyLens_android

## Current milestone

- Approved indigo/lavender light and slate/ocean-blue dark palettes, following the system appearance.
- Clickable Home, property preparation, saved properties, details and editing screens.
- Local SQLite property storage on a background executor, with validation and deletion confirmation.
- Draft fields and navigation restored after activity recreation.
- Rear-camera video recording, offline saving and playback are implemented on the recording branch. Call joining remains a placeholder. Compass, video sharing/export, accounts and sync are not implemented yet.

## Open and build

Open this directory (PropertyLens-Android) in Android Studio and let Gradle sync finish. Select the app configuration and an emulator/device, then Run. Minimum Android 11 (API 30); compile/target API 36. Uses JDK 17 or newer, Gradle 8.13, AGP 8.13.2, Kotlin 2.2.21 and AndroidX Activity 1.10.1.

Local command-line build on this Mac:

```sh
JAVA_HOME=/Users/bullzeye/Library/Java/JavaVirtualMachines/jbr-21.0.11/Contents/Home ./gradlew assembleDebug lintDebug
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

Keep `local.properties`, .idea, .gradle, build output and credentials outside Git. The existing package/application ID remains `com.ganesham.app` to preserve local installs; the visible name is PropertyLens. Final package naming, app icon polish, full localization and backup policy review remain release-preparation work.

## Acceptance checklist

1. Open Home in light/dark mode and verify readable cards, safe system-bar spacing and navigation.
2. Try to save empty fields and confirm validation. Save a land/flat/building record.
3. Open Saved, inspect and edit a property; restart the app and confirm persistence.
4. Cancel deletion, then confirm deletion; verify the record is removed.
5. Rotate while entering property details and confirm the draft survives.
6. Verify system Back returns through the screens. Record/call placeholders must not claim to be working tools.

No iOS files belong in this repository. The foundation is the first main-branch baseline. New camera work is developed separately on a feature branch.

## Verification for this milestone

`assembleDebug`, `lintDebug` and `connectedDebugAndroidTest` passed on the Pixel 8 API 36 emulator. The isolated PropertyStore test verifies create, persistence after reopening the database, edit and delete without touching user records. Run it with `./gradlew connectedDebugAndroidTest` while a device/emulator is connected. UI was visually inspected; the full acceptance checklist above still needs user review. Lint reports no errors; informational recommendations remain for newer tool/dependency versions, KTX color helpers and backup policy.

## Settings

The Home gear opens Settings with persistent System/Light/Dark appearance and a device-local name, optional email and Buyer/Seller/Broker role. Local profile clearing preserves properties. Authentication and cloud sync are not implemented.

## Rear-camera recording milestone

Branch: `codex/android-walkthrough-recording` (separate from the foundation on main).

From Home or a saved property choose **Record walkthrough**. Allow camera access, tap Record, then Stop & save. The Saved button opens offline playback. Property-specific entry points filter the saved library to that property; Home shows all recordings. Capture uses CameraX 1.4.2, requests HD with device fallback, and never enables audio or asks for microphone permission. A visible REC state and keep-screen-on flag are active while recording.

Completed MP4 files and atomic JSON sidecars are stored privately under files/walkthroughs. Incomplete recordings are not listed. No cloud upload or external storage permission is involved. Uninstalling the app removes local recordings. Hold the phone upright; landscape/large-screen behaviour is not yet validated. Backgrounding or leaving recording requests stop/finalization; permission denial and save failure show feedback.

### Acceptance checks before merging

- Physical Android device: permission denial, allowing permission in Settings, preview restart, record/stop/save, offline playback after app restart, and screen/background interruptions.
- Check the saved frame orientation and preview framing, particularly on hardware with a nonstandard camera sensor orientation.
- Verify low-storage and camera-in-use failure feedback.
- Compass and video sharing are separate future milestones; this branch does not claim iOS feature parity yet.

Automated recorder test: `./gradlew connectedDebugAndroidTest`. The test grants camera permission on the test device, records a short clip, verifies duration and a decodable frame, confirms no audio track, and cleans up only its own recordings. It also runs the independent property-database regression test. Requires a connected device/emulator with a functioning rear camera.

### Recorder verification

`assembleDebug`, `lintDebug`, and both connected instrumentation tests passed on the Pixel 8 API 36 emulator. The recorder test produced a real camera clip, checked positive duration and video width, decoded a frame, and confirmed that the file contains no audio track. Physical-device camera behaviour and the interruption checklist remain pending. Lint reports no errors; dependency-update, portrait-orientation, backup-policy and style recommendations remain. This feature branch is not merged to main until device review.
