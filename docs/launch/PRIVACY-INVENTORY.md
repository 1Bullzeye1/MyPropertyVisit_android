# Android privacy inventory

Source reviewed 10 November 2026. Local property records use SQLite. Preferences hold optional profile name/email/role and theme. JPEGs, MP4s, room metadata and visit JSON are in private files. Compass uses device sensors; no GPS position request, contacts access, microphone input or MyPropertyVisit online account is implemented.

The main manifest declares Camera permission and no INTERNET permission. Backup is disabled with android:allowBackup=false; verify merged manifest and platform behavior on the release target. CameraX/AndroidX dependencies must be included in the final SDK review. No advertising or analytics SDK was found in reviewed declared dependencies.

Exports write Photos to Pictures/MyPropertyVisit and videos to Movies/MyPropertyVisit through MediaStore, publishing completed entries from an IS_PENDING state. They do not require broad photo-library reads. Sharing grants temporary read access to chosen FileProvider content URIs. Third-party share destinations and platform Photos backup have separate handling. Export does not provide a full project backup or embed editable dimensions/notes.

Suggested Play Data safety position: no developer collection and no developer sharing, **conditional on final merged-manifest/dependency review and the user-directed sharing exceptions**. Local processing and deliberate user-directed transfers must be interpreted using Google's actual form definitions. Support email and website provider logs are separate channels and must be reviewed. This draft does not submit or certify declarations.

Sources: [Google Data safety guidance](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en), [Android data handling definitions](https://developer.android.com/about/versions/14/features/app-metadata), [Google User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311).

Photo/property deletion and profile clearing have different scopes. No dedicated video/session deletion control was found. Clearing storage/uninstalling removes private app records; separately remove Gallery exports, recipient copies and backups. Do not promise recovery, end-to-end encryption or no platform traffic based solely on first-party source review.
