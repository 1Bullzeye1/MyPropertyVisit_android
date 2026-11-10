# MyPropertyVisit for Android

Independent native Kotlin Android app. Repository: https://github.com/1Bullzeye1/MyPropertyVisit_android. No iOS source is included here. See [launch documentation](docs/launch/README.md) for Android scope, privacy inventory and release gates.

## Current offline features

Property records in SQLite, local profile, purple light/dark/system themes and matching menus; rear-camera silent video, native pause/resume and photo capture; embedded compass in photos/new videos; hardware-aware zoom buttons; capture sessions; full-screen photos and video playback; manual shared room dimensions/notes; individual and batch photo/video sharing and Gallery saving. Home provides room-gallery and media-library access.

Live calling, verified accounts, cloud sync and full project backup/restore remain outside launch. Existing calling placeholders require release cleanup. Gallery exports preserve compass artwork, not all editable room/project metadata. Android price remains an owner decision; iOS USD 4.99 pricing does not grant a cross-platform entitlement.

## Open and build

Open this repository root in Android Studio and finish Gradle sync. Use the Android App run configuration with module app and Default Activity; do not create a Java/main-class configuration. Select an emulator or a USB-debugging device.

Minimum Android 11/API 30; current compile/target API 36. Gradle 8.13, AGP 8.13.2, Kotlin 2.2.21, CameraX 1.4.2. The known working local Java runtime is JBR 21; the newer Android Studio JBR previously failed with this toolchain.

```sh
export ANDROID_HOME="$HOME/Library/Android/sdk"
export JAVA_HOME="$HOME/Library/Java/JavaVirtualMachines/jbr-21.0.11/Contents/Home"
./gradlew --offline :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
```

Debug APK: app/build/outputs/apk/debug/app-debug.apk. Instrumentation requires a functioning device/emulator camera. Avoid concurrent UI automation sessions while running instrumentation. Keep local.properties, .idea, .gradle, build output, signing files and credentials out of Git.

Application ID com.ganesham.app is intentionally retained for installed-data continuity; visible name is MyPropertyVisit. A signed release AAB and Play review are separate release steps.

## Storage and permissions

Camera permission only in the main manifest. No microphone recording, GPS location collection, contacts integration or first-party network client. Property data is local SQLite; profile/theme uses preferences; videos/JSON live in files/walkthroughs and photo/room/visit records in files/room-media. Backup is disabled in the manifest. User-directed Gallery export uses MediaStore, and sharing uses temporary read grants through FileProvider. Other apps and platform media sync have their own policies.

## Validation baseline

The export milestone passed debug builds, Android lint and **11 Pixel 8 emulator instrumentation tests**, including photo-byte preservation, Gallery publication, selection controls, and actual recorded MP4 playback after export. Physical-device verification of the latest export build remains pending. Previous Motorola reports make real-device framing, saved-video playback and compass bounds explicit release tests. Run interruption, low-storage, denied-permission, large-gallery, theme, accessibility and update-retention checks before shipping.

The release checklist records these as open actions rather than implying emulator success is production acceptance.
