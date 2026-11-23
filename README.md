# Easy Class Share 🎒

A small, kid-friendly Android app for sharing links (or short messages) with
every tablet in a classroom. **One app, two roles:**

| Role | Who | What it does |
|------|-----|--------------|
| 🚀 **SEND** | usually the teacher | Paste or type a link and send it to the whole class with one big button |
| 📬 **RECEIVE** | the kids' tablets | Waits for links; each one shows up as a big card with a one-tap **Open** button |

## How to use it in class
1. Connect all tablets to the **same Wi-Fi** (or the teacher's phone hotspot).
2. Kids tap **RECEIVE**. The teacher taps **SEND**, pastes a link and taps
   **🚀 Send**. A chime plays and the link pops up on every tablet.

Tip: in Chrome, the teacher can tap *Share → Easy Class Share* to open the
Send screen with the link already filled in.

## How it works
- No server, no accounts, no internet needed: messages are **UDP broadcasts**
  on the local network (port `47474`), JSON-encoded (`ShareMessage`, protocol v2).
- Each message is sent 5× to survive dropped packets; receivers ignore
  duplicates by id. "Send again" re-sends with the same id, so only tablets
  that missed it will show it.
- Only `http`/`https` links are opened. Everything else is shown as text with a
  Copy button.
- Receiving happens while the Receive screen is open (the screen is kept awake).

> Some school/guest Wi-Fi networks block device-to-device traffic
> ("client isolation"). If nothing arrives, try a phone hotspot.

## Building
Requirements: Android Studio (Koala or newer) with JDK 17, Android SDK 34.

1. *File → Open* this folder. Android Studio downloads Gradle 8.9 as set in
   `gradle/wrapper/gradle-wrapper.properties`.
2. Run the `app` configuration on a device or emulator (min Android 8.0 / API 26).

To get a command-line `./gradlew`, run `gradle wrapper --gradle-version 8.9`
once (or let Android Studio generate it), then `./gradlew assembleDebug test`.

## Project layout
```
app/src/main/java/com/easyclassshare/app/
├── MainActivity.kt          # single activity, screen switching, share intent
├── ClassShareViewModel.kt   # app state for both roles
├── data/                    # ShareMessage, LinkUtils
├── net/ClassNetwork.kt      # UDP broadcast send/receive
└── ui/                      # Home, Sender, Receiver screens + theme/components
```

## Versioning
The project uses [Semantic Versioning](https://semver.org/) and git tags
(`v1.0.0`, …). For a release: update `versionCode`/`versionName` in
`app/build.gradle.kts`, add an entry to `CHANGELOG.md`, commit, then
`git tag -a vX.Y.Z -m "..."`.
