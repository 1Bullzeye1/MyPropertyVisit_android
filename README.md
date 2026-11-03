# PropertyLens for Android

Independent native Kotlin Android app. Repository: https://github.com/1Bullzeye1/PropertyLens_android

## Current milestone

- Approved indigo/lavender light and slate/ocean-blue dark palettes, following the system appearance.
- Clickable Home, property preparation, saved properties, details and editing screens.
- Local SQLite property storage on a background executor, with validation and deletion confirmation.
- Draft fields and navigation restored after activity recreation.
- Call joining and walkthrough recording are clearly labelled placeholders. Camera recording, compass, playback, video export, accounts and sync are not implemented in this Android milestone.

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
