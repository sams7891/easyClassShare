# Guide for AI assistants

This file is for an AI (or a new developer) starting work on Easy Class Share. Read it
before changing code. The user-facing docs are in [README.md](README.md), the history is in
[CHANGELOG.md](CHANGELOG.md).

## What the app is
An Android app (Kotlin, Jetpack Compose, one activity) that a teacher uses to control the
student devices in a classroom:
- share links and short messages to the whole class, the online devices only, or single devices
- switch sound off on all devices or on single ones (timed mutes too)
- **Eyes on me**: a full-screen message on every student device
- **Open the app on all devices**: brings the app to the front on the student devices
- see each device's online status, battery level and which shared links it opened

There are two modes:
- **Online classes** (the main mode): Firebase Realtime Database plus anonymous sign-in. Works on any network.
- **Same Wi-Fi**: UDP broadcasts on port 47474, with no server and no setup (`net/ClassNetwork.kt`, `ClassShareViewModel.kt`).

Roles: **student device**, **teacher**, **school admin** (teacher rights in every class of
its school), **super admin** (added by hand in the Firebase console, admin of every school).
A device is either a student device or a teacher/admin device, never both.

## Layout
```
app/src/main/java/com/easyclassshare/app/
├── MainActivity.kt          screen switching (enum Screen, Crossfade), share intent, theme
├── EasyClassShareApplication.kt
├── ClassViewModel.kt        all online-class state and actions (teacher, admin, student)
├── ClassShareViewModel.kt   same-Wi-Fi mode, and vm.screen (the current screen)
├── online/
│   ├── ClassRepository.kt   EVERY Firebase read/write; the data layout is in its header comment
│   ├── LocalSetup.kt        the device's memberships (SharedPreferences), kept after restarts
│   ├── Models.kt            data classes, SoundPolicy, JoinCodes
│   └── OnlineConfig.kt      Firebase setup from BuildConfig (built from app/google-services.json)
├── service/
│   ├── ClassService.kt      student devices: foreground service that listens to the classes,
│   │                        enforces sound, shows Eyes on me, opens the app on request
│   ├── LockService.kt       accessibility service: Eyes on me over the notification shade,
│   │                        and blocks the volume keys while muted
│   ├── AttentionOverlay.kt  the Eyes on me window (overlay or accessibility type)
│   ├── SoundEnforcer.kt     saves the volume, mutes it, and restores it
│   ├── Notifications.kt     all notifications and channels
│   ├── DevicePermissions.kt checks for and opens the 4 permissions a student device needs
│   └── BootReceiver.kt      restarts ClassService after a reboot or an app update
├── settings/AppSettings.kt  language and light/dark; wrap() applies both to a Context
├── update/AppUpdater.kt     updates from the GitHub releases (download, then Android's installer)
└── ui/                      one file per screen, plus components/ and theme/
app/src/main/res/values{,-lv,-ru}/strings.xml   English, Latvian, Russian
database.rules.json          GENERATED. Do not edit; edit tools/make_rules.py
tools/make_rules.py          writes the Firebase security rules
```

## Firebase data
The authoritative list is the comment at the top of `online/ClassRepository.kt`. In short:
`joinCodes/{code}`, `classSecrets/{cid}`, `classes/{cid}/{info,teachers,devices,sound,messages,inbox,opened,attention,openApp}`,
`schools/{sid}/{info,admins,classes}`, `schoolSecrets/{sid}`, `superAdmins/{uid}`.
Every device signs in anonymously, and its uid stands for "this device".

The rules decide who may do what. When you add or change a path:
1. edit `tools/make_rules.py`
2. run `python3 tools/make_rules.py`
3. tell the user to publish `database.rules.json` in the Firebase console. Apps already
   installed keep running against the published rules, so a new path is refused
   ("permission denied") until the user publishes them.

## Patterns to keep
- **Reading data:** use `Query.values()` in ClassRepository. It waits for the sign-in, and
  when Firebase cancels the listener it attaches it again after a back-off. Don't make a
  raw `addValueEventListener` flow that ends on an error: that froze the teacher's tiles before 1.4.0.
- **Actions in ClassViewModel:**
  - `runAction` / `teacherAction` / `schoolAction` set `busy` and refuse to run while another action runs.
  - `quickAction` is for controls that must always respond (sound, Eyes on me, open the app).
    Don't gate those buttons on `cvm.busy`.
- **Errors** are string resource ids in `cvm.error`, shown with `ErrorText`.
- **Texts:** every string goes into all three `strings.xml` files. Say **"device"**, never
  "tablet", in strings and docs. Some old key names contain "tablet"; don't rename them.
- **The student device shows Eyes on me in its own language** (`R.string.attention_default_text`).
  The `text` that the teacher writes to Firebase is only there for older app versions.
- **Language and light/dark:** `AppSettings.wrap()` is applied in `attachBaseContext` of the
  activity, the application and the services. Changing either setting recreates the activity.
- **Services** use the `texts` context in ClassService, so notifications follow the app language.
- **Background starts:** the app may only start an activity from the background when it
  has the overlay permission (and targetSdk is 34). Otherwise, post a notification.
- **Comments** say *why*, in plain short sentences. Keep the existing style.

## Building and testing
- The developer's machine has **no JDK or Android SDK**. Builds run in Android Studio or on
  GitHub Actions (`.github/workflows/android.yml`: `gradle test assembleRelease`, Gradle 8.9,
  JDK 17, compileSdk/targetSdk 34, minSdk 26). If you can't compile, review your code very
  carefully (imports, signatures) and let CI check it before tagging a release.
- Unit tests are in `app/src/test` (JVM only, using the real org.json). Keep Android main-thread
  objects lazy in code that the tests touch (see `AppUpdater.scope`).

## Releasing
1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Date the CHANGELOG entry (Keep a Changelog, SemVer).
3. Commit "Version X.Y.Z".
4. Push the branch and wait for CI to pass.
5. Tag `vX.Y.Z` on the same commit on `main` and push the tag. CI then builds the signed APK
   and creates the GitHub Release.
6. Tags with a `-` (for example `v1.5.0-beta.1`) become pre-releases. The in-app updater
   ignores them.

Installed apps update themselves from the latest release (`AppUpdater`). An update can
only be installed over an APK signed with the same key, which is the case for releases
signed in CI.

## Working with this user
- Don't add AI co-author or "Generated with" lines to commits or PRs.
- Commit messages: a short summary line, optionally followed by bullet points.
  Version bumps get their own commit.
- `main` and `feature/online-classes` have been kept at the same commit for releases.
