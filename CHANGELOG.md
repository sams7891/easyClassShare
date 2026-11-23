# Changelog

All notable changes to this project are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/) and the
project uses [Semantic Versioning](https://semver.org/).

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
