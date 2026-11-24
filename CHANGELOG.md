# Changelog

All notable changes to this project are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/) and the
project uses [Semantic Versioning](https://semver.org/).

## [1.3.0] - 2026-11-24
### Added
- **Online classes** (Firebase): one-time setup per device with a class code;
  sharing works across different networks and in the background.
- Several teachers per class (teacher code), with equal rights.
- **Manage class** for teachers: class and teacher codes (renewable), list of
  student devices with online status, remove devices or teachers.
- **Sound control**: teachers can switch sound off for the whole class or for
  single devices. Muted devices are held at volume zero until a teacher allows
  sound again.
- Student devices reconnect automatically after a restart or app update, and
  can only leave a class with the teacher code.
- Teachers can be in **several classes** on one device and switch between
  them from the class name at the top (with "Add a class").
- New, calmer look: real icons instead of emoji, plainer wording, a classroom
  colour palette (chalkboard green, paper, pencil yellow).
- New teacher screen: Sound and "Eyes on me" tiles at the top, a **Share** tab
  and a **Devices** tab; codes and teachers moved to **Class settings**.
- **Read receipts**: "Opened by 18 of 24" on every shared link, with the names
  of devices that haven't opened it. Tapping a link notification opens it directly.
- **Send to single devices** instead of the whole class.
- **Timed mute** (10 to 60 minutes); sound comes back by itself.
- **Battery level and "last seen"** for every device.
- **Delete** something shared from all devices.
- **Eyes on me**: a full-screen message on every student device until the
  teacher ends it.
- Teachers can **rename devices**.
- **Schools and school admins**: an admin creates a school (or joins with an
  admin code) and can manage every class of the school with full teacher
  rights, without being one of its teachers. Teachers add a class to a school
  with the school code when creating it; admins can also create classes.

- **Start screen** for teachers and admins listing all their schools and
  classes; teachers and admins can be in several schools.
- Student devices can be in **several classes** with one shared inbox.
- **Send to online devices only.**
- **Copy** buttons for all codes.
- **Super admin**: one device (set in the Firebase console) sees and manages
  every school.

### Removed
- The chime when a message arrives while the app is open.

### Fixed
- Muting the class no longer disabled the send button and showed "Sending…".

### Changed
- The same-Wi-Fi mode is still there for quick sharing without internet.

## [1.2.1] - 2026-11-23
### Fixed
- Release build: tolerate whitespace in the keystore secret and report
  clearly which signing secret is wrong. (1.2.0 was never published.)

## [1.2.0] - 2026-11-23
### Added
- GitHub Actions workflow: tests + signed release APK on every push;
  tagged versions are published as GitHub Releases with the APK attached.
- Release signing configuration read from CI secrets.

## [1.1.0] - 2026-11-23
### Removed
- Class "animal" picker: every Receive screen on the same Wi-Fi now gets
  all messages. Message protocol bumped to v2 (no `room` field), so 1.0.0
  and 1.1.0 tablets can't talk to each other; update all tablets together.

## [1.0.0] - 2026-11-23
### Added
- Single app with two roles: **Send** and **Receive**.
- Class "animal" channels so multiple classes can share one Wi-Fi.
- Wi-Fi UDP broadcast sharing (no server or internet required).
- Big, colourful, kid-friendly UI with emoji, chime and "NEW!" badge.
- One-tap Open for links, Copy for text messages.
- "Send again" for tablets that joined late.
- Share links straight from other apps into the Send screen.
