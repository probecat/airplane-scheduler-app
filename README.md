# Airplane Scheduler

[![CI][ci-image]][ci-url] [![Release][release-image]][release-url] [![License][license-image]][license-url]

Turns airplane mode on and off on a schedule, so your phone goes quiet at night without you having to remember.

<a href="https://f-droid.org/packages/io.github.probecat.airplanescheduler/"><img src="https://f-droid.org/badge/get-it-on.png" alt="Get it on F-Droid" height="80"></a>

<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1.png" alt="Schedules" width="250"> <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/2.png" alt="Editing a schedule" width="250"> <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/3.png" alt="Schedules in light theme" width="250">

See [Releases][release-url] for signed builds (same as F-Droid's).

## Features

- Any number of schedules, one-off or repeating on any days of the week.
- Supports overnight time ranges automatically.
- Optionally turns off Wi-Fi at the start and back on at the end.
- Undoes only what it changed: airplane mode or Wi-Fi that you set yourself stays as you left it.
- An optional reminder an hour before a schedule starts if Shizuku isn't ready.
- No internet access, ads or tracking.

## Requirements

- Android 13 or later.
- [Shizuku](https://shizuku.rikka.app/).

## Building

The toolchain is managed with [mise](https://mise.jdx.dev/). Check [mise.toml](mise.toml) for setup and build commands.

## License

[GPL-3.0-or-later](LICENSE).

[ci-url]: https://github.com/probecat/airplane-scheduler-app/actions/workflows/ci.yml
[ci-image]: https://github.com/probecat/airplane-scheduler-app/actions/workflows/ci.yml/badge.svg
[release-url]: https://github.com/probecat/airplane-scheduler-app/releases/latest
[release-image]: https://img.shields.io/github/v/release/probecat/airplane-scheduler-app
[license-url]: LICENSE
[license-image]: https://img.shields.io/github/license/probecat/airplane-scheduler-app
