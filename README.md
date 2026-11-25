# Easy Class Share 🎒

A small, kid-friendly Android app for sharing links (or short messages) with
every device in a classroom. It works in two ways:

- **🏫 My class (online):** a one-time setup puts each device into a class.
  Works on **any network**, supports **several teachers per class**, and lets
  teachers **manage the class** and **switch sound off** for everyone or for
  single devices.
- **📶 Same Wi-Fi:** no setup and no internet, for quick sharing in one room.

In same-Wi-Fi mode there are two roles:

| Role | Who | What it does |
|------|-----|--------------|
| 🚀 **SEND** | usually the teacher | Paste or type a link and send it to the whole class with one big button |
| 📬 **RECEIVE** | the kids' devices | Waits for links; each one shows up as a big card with a one-tap **Open** button |

## 📲 Download & install
Get the latest **`.apk`** from the
[Releases page](https://github.com/sams7891/easyClassShare/releases/latest),
open it on the device and tap **Install** (allow "install unknown apps" if asked).
Install the same version on every device.

## 🏫 Online classes
### Setting up a class (once)
1. **Teacher:** open the app → **My class** → **Teacher** → **New class**.
   Type your name and the class name. You get two codes (see *Manage class*):
   - a **class code** (6 letters) for the students,
   - a **teacher code** (8 letters) for other teachers of this class.
2. **Each student device:** **My class** → **Student**, type the child's name
   and the class code. Done: the device stays in the class, also after a restart.
3. **Other teachers:** **My class** → **Teacher** → **Join as a teacher** with the
   teacher code. All teachers of a class have the same rights.
4. **More classes:** a teacher taps the class name at the top → **Add a class**,
   and switches between classes the same way.

### Schools and school admins (optional)
A school admin can manage **every class of the school**, also classes they
don't teach, with everything a teacher can do (sound, Eyes on me, sharing,
devices, codes, teachers).

1. **Admin:** **Set up** → **School admin** → **New school**. You get:
   - a **school code** (7 letters) for the teachers of the school,
   - an **admin code** (10 letters) for other admins (**Join as an admin**).
2. **Teachers:** when creating a class (**New class**), enter the school code in
   *School code (optional)*. The class then shows up in the school.
3. Admins can also create classes themselves (**New class** on the school
   screen) and give the class's teacher code to the teacher.
4. The admin's home screen lists all classes; tap one to open it. School
   codes and admins are in **School settings**.

Teachers and admins can be in several classes and schools at once. The app
opens on a **start screen** listing their schools and their classes (grouped by
school); tap one to open it.

A **student device** can also be in several classes (**Join another class** at
the bottom of its inbox). It shows one inbox for all classes, is muted while
*any* of its classes has sound off, and shows "Eyes on me" from any class.

### Super admin (the app's owner)
The super admin sees **every school** on the start screen and has admin rights
in all of them. Only someone with access to the Firebase console can make a
device super admin:

1. In the app on that device: start screen → **⋮ → Device ID** → **Copy**.
2. Firebase console → **Realtime Database → Data**: add a child `superAdmins`
   at the top level, and inside it a child whose **key is the device ID** and
   whose **value is `true`**.
3. Reopen the app. *All schools (super admin)* appears on the start screen.

Remove the entry to take the rights away again.

### In class
The teacher screen has two status tiles at the top and two tabs:

- **Sound**: tap to switch sound on, mute until you switch it back on, or mute
  for 10 to 60 minutes (sound then comes back by itself).
- **Eyes on me**: tap to cover every student screen with "Eyes on your teacher";
  tap again to end it. Over other apps this needs the device's *Display over
  other apps* permission (the Devices tab shows which devices haven't allowed it
  yet). To stop students getting into the notifications and the device settings
  while it is on, also turn on the **Eyes on me lock** (Settings → Accessibility
  → Easy Class Share). On Android 13 and newer, an APK installed from outside a
  store first needs *App info → ⋮ → Allow restricted settings*.
- **Share** tab: type or paste a link, choose **To: Whole class**, **Online
  devices only**, or single devices, and send. Every shared item shows **"Opened by 18 of 24"**; tap it
  to see who hasn't opened it yet. Shared items can be deleted from all devices.
- **Devices** tab: every device with online / last seen, battery level, and its
  own sound switch. The ⋮ menu renames a device, sends something only to that
  device, or removes it from the class.
- **Class settings** (gear icon): class and teacher codes (renewable), the
  teachers of the class, and leaving the class.

### How the sound lock works
While sound is off for a device, the app sets the device's volume (media,
notifications, system and ring) to zero and puts it back to zero if a child
turns it up. Only a teacher can switch it on again; then the old volume comes
back. This works while the app is installed and running (a small "Connected to
my class" notification shows that). A child could still force-stop or
uninstall the app; to prevent that, the devices need a device-management
(MDM / kiosk) setup, which is outside this app.

A student device can only leave the class when a teacher types the teacher code
(or a teacher removes it in the Devices tab).

### One-time Firebase setup (for whoever builds the app)
Online classes use a free [Firebase](https://firebase.google.com/) project.
Without it the app still builds and the same-Wi-Fi mode works; the
*My class* button is simply hidden.

1. Go to the [Firebase console](https://console.firebase.google.com/) and
   **create a project** (Google Analytics is not needed).
2. **Build → Authentication → Get started → Sign-in method → Anonymous → Enable.**
3. **Build → Realtime Database → Create database**, pick a location (for
   schools in the EU: **Belgium, europe-west1**), start in **locked mode**.
4. In the database's **Rules** tab, replace everything with the content of
   [`database.rules.json`](database.rules.json) and click **Publish**.
5. **Project settings (⚙️) → Your apps → Android**: package name
   `com.easyclassshare.app`, register the app and **download
   `google-services.json`** (download it *after* step 3, so it contains the
   database address).
6. Put `google-services.json` into the `app/` folder and commit it. The next
   build (also on GitHub Actions) has online classes switched on.

`google-services.json` is not secret: Firebase says it is fine to ship it in
apps. What protects the data are the database rules from step 4: only teachers
of a class (and the admins of its school) can send, see the codes, change sound
or remove devices; students only see their own class.

The rules are generated by [`tools/make_rules.py`](tools/make_rules.py). After
changing it, run `python3 tools/make_rules.py` and publish the new
`database.rules.json` in the console again.

Data stored online: class name, teacher and student first names, the shared
links/messages (newest 50 shown), which devices opened them, and each device's
online status and battery level. No e-mail
addresses or accounts.

## 📶 Same-Wi-Fi mode
1. Connect all devices to the **same Wi-Fi** (or the teacher's phone hotspot).
2. Kids tap **RECEIVE**. The teacher taps **SEND**, pastes a link and taps
   **🚀 Send**. A chime plays and the link pops up on every device.

Tip: in Chrome, the teacher can tap *Share → Easy Class Share* to open the
Send screen with the link already filled in.

## How same-Wi-Fi mode works
- No server, no accounts, no internet needed: messages are **UDP broadcasts**
  on the local network (port `47474`), JSON-encoded (`ShareMessage`, protocol v2).
- Each message is sent 5× to survive dropped packets; receivers ignore
  duplicates by id. "Send again" re-sends with the same id, so only devices
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
├── MainActivity.kt              # single activity, screen switching, share intent
├── EasyClassShareApplication.kt # starts the online-class pieces
├── ClassShareViewModel.kt       # same-Wi-Fi mode state
├── ClassViewModel.kt            # online class state (setup, send, manage)
├── data/                        # ShareMessage, LinkUtils
├── net/ClassNetwork.kt          # same-Wi-Fi: UDP broadcast send/receive
├── online/                      # Firebase: ClassRepository, codes, saved setup
├── service/                     # student background service, sound lock, boot start
└── ui/                          # screens + theme/components
database.rules.json              # Firebase security rules (paste into the console)
```

## Automatic builds (GitHub Actions)
`.github/workflows/android.yml` runs the tests and builds a release APK on every
push and pull request (downloadable under *Actions → run → Artifacts*).
Pushing a tag `vX.Y.Z` also creates a **GitHub Release** with the APK attached.

Release APKs are signed with the project key, provided via these repository
secrets (*Settings → Secrets and variables → Actions*):

| Secret | Value |
|--------|-------|
| `ANDROID_KEYSTORE_BASE64` | base64 of the PKCS#12 keystore file |
| `ANDROID_KEYSTORE_PASSWORD` | keystore password |
| `ANDROID_KEY_ALIAS` | key alias |
| `ANDROID_KEY_PASSWORD` | key password (same as keystore password for PKCS#12) |

Never commit the keystore. Keep a backup: without it, updates can't be
installed over the existing app.

## Versioning
The project uses [Semantic Versioning](https://semver.org/) and git tags
(`v1.0.0`, …). For a release: update `versionCode`/`versionName` in
`app/build.gradle.kts`, add an entry to `CHANGELOG.md`, commit, then
`git tag -a vX.Y.Z -m "..."` and `git push origin main --tags`.
