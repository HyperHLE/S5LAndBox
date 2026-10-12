# S5LBox

**Classic iPhone OS. On your iPhone.**

S5LBox runs real iPhone OS 3.1.3 by emulating the iPhone 3G's hardware.
Rediscover its home screen, apps, and tweaks inside an app on your iPhone—no
host jailbreak or JIT setup required.

## What you can do

- Create separate machines, choose their storage size, and save and resume sessions.
- Install compatible IPA apps without shutting down the machine.
- Add Cydia and manage tweaks with the built-in Packages browser.
- Use guest networking, experimental audio playback, and optional microphone input.

## Get started

You'll need a 64-bit iPhone running **iOS 13 or later** and several gigabytes
of free storage.

1. Get `S5LBox.ipa` from a successful **main** run of the
   [iOS build workflow](https://github.com/j0shua-SYSON/S5LBox/actions/workflows/ios-build.yml?query=branch%3Amain).
   Development builds must be signed for your device before installing on stock iOS.
2. Open S5LBox and choose **Download iPhone OS**, or **Import IPSW** to use your
   own iPhone 3G / iPhone OS 3.1.3 firmware.
3. Keep the app open while setup validates and prepares the firmware, then tap
   **Start iPhone OS**. No decryption keys to enter.

Use **Save & close** to pick up where you left off. Advanced options live in
Settings. Firmware downloads come directly from Apple; no Apple firmware is
bundled with S5LBox.

## Before you try it

S5LBox is experimental and preparing for its first release. Performance varies
by device, and some apps, tweaks, and hardware features are not yet fully
supported. Older online services may no longer work. Back up important guest
data before experimenting.

## Explore further

- [Installing apps](docs/live-ipa-usb.md) · [Packages and tweaks](docs/native-package-manager.md)
- [Audio](docs/audio.md) · [Storage options](docs/DISK-SIZE-PRESETS.md)
- [Architecture](docs/ARCHITECTURE.md) · [Detailed technical reference](docs/README-DETAILED.md)

### Build the core

The emulator core is portable C. Its public tests do not need Apple firmware.

```sh
cmake -S . -B build -DCMAKE_BUILD_TYPE=Release
cmake --build build --config Release
ctest --test-dir build -C Release --output-on-failure
```

The iOS app uses Xcode. See the [build workflow](.github/workflows/ios-build.yml)
for the app build and packaging steps.

## License

[MIT](LICENSE). Created by [j0shua-SYSON](https://github.com/j0shua-SYSON).
The guest package helper includes Apple Csu code under
[APSL](app/Resources/GuestPackages/GuestPackageNotices.txt).
S5LBox is an independent project, not affiliated with or endorsed by Apple.
