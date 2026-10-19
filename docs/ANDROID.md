# S5LAndBox — the Android port

S5LAndBox runs the same portable S5L8900 core as S5LBox on Android: the real
iPhone OS 3.1.3 (7E18, iPhone1,2 / n82ap) boots to SpringBoard on an ARM1176
interpreter. Only the platform shell is new. The JIT and the static AArch64
engine are Apple-only and are not built here; the portable interpreter is the
whole execution path.

The port lives in `android/` and builds an installable APK in GitHub Actions
(`.github/workflows/android-build.yml`). No firmware, keys or Apple binaries are
in the repository.

## Requirements

- Android 8.0 (API 26) or later.
- An `arm64-v8a` phone or an `x86_64` emulator; no 32-bit ABIs.
- About 1.5 GB of free storage: the IPSW is read in place, and the unpacked
  root filesystem plus a machine's writable image take roughly 450 MB each.
- An iPhone1,2 iPhone OS 3.1.3 IPSW. Import validates the archive, checks the
  stock payload hashes and refuses another device. A developer switch on the
  import screen accepts an otherwise-valid IPSW whose payload hashes differ.

## Getting the APK

Every push to `android-port` and every pull request against `main` builds the
debug APK. Open the run under **Actions → android-build** and download the
`s5landbox-debug-apk` artifact. A manual run (`workflow_dispatch`) also builds
the release variant. Both are signed with the debug key so they install
directly; neither is a store artifact.

## First run

1. **Firmware.** Tap the download icon and choose an IPSW. The import unpacks
   `kernel.macho`, `devicetree.bin` and the root filesystem into app storage,
   with a progress bar and a report. This happens once.
2. **New machine.** Each machine owns a writable copy of the root filesystem,
   so changes in the guest never touch the imported firmware. The first boot
   copies roughly 450 MB and shows its own progress.
3. **Use it.** The emulator screen has a 320×480 guest display with touch input
   and controls for Home, Power, Volume up/down and the silent switch.

## What works

| Area | State |
| --- | --- |
| Boot to SpringBoard | Yes |
| Touch (tap, drag, swipe) | Yes; one pointer at a time |
| Home, Power, Volume, silent switch | Yes, with hold semantics |
| Multiple machines, rename, delete | Yes |
| Machine options (`VMOptions`) | Yes; applied at the next start |
| IPSW import through the system file picker | Yes |
| Snapshots (create, restore, delete) | Yes |
| Audio output (48 kHz stereo) | Yes |
| Guest console (`uart0`) | Yes |
| Guest networking | Through the portable boot layer, given `INTERNET` |
| Pause and resume, checkpoints | Yes |
| Save/restore an older snapshot | Yes, including disk copy-on-write replay |
| USB services: guest files (AFC), IPA install | Not yet |
| Cydia / package installation | Not yet |
| Microphone capture | Not yet; the option only toggles the guest side |
| Background execution | The machine pauses when the app is stopped |

## Storage

Everything lives under the app's external files directory, which needs no
permission:

```
Android/data/com.tayga16.s5landbox/files/
  firmware/                 kernel.macho, devicetree.bin, rootfs.img
  Machines/
    instances.txt           machine names, options and last-opened time
    <id>/
      rootfs-work.img       this machine's writable disk
      state.snap            latest checkpoint RAM (when stopped with a checkpoint)
      state.snap.mdstate    disk-bridge sidecar for that checkpoint
      state.snap.restore-once
      snapshots/<snapshot-id>/
        state.snap, state.snap.mdstate, disk.cow, meta
```

Uninstalling the app removes all of it. Pulling the directory over USB is the
easiest way to back a machine up.

## Snapshots

A snapshot is the machine's RAM and disk at one moment. Taking one stops the
machine with a checkpoint first, so the saved memory matches the disk.

Disk history is stored as a copy-on-write overlay: the first time the guest
writes a 4 KiB block after a snapshot, its previous contents are appended to
that snapshot's `disk.cow`. Restoring an older snapshot therefore needs the
target overlay *and every newer one*. The engine replays them newest-first so
that older values win, then publishes the target's checkpoint and removes the
newer snapshot directories.

Because of that, restoring an older snapshot discards every newer snapshot and
every guest change made after it. The UI warns before doing it and shows how
many snapshots will be deleted.

A checkpoint saved without a snapshot keeps the single resume slot behaviour of
the iOS app: `Save & stop` writes `state.snap` plus the marker, and the next
boot resumes.

## Building locally

You need JDK 17, Android SDK platform 35, build-tools 35.0.0, NDK
27.0.12077973 and CMake 3.22.1. The repository does not commit a Gradle wrapper
jar; use Gradle 8.9 or newer.

```sh
gradle -p android :app:assembleDebug
# android/app/build/outputs/apk/debug/app-debug.apk
```

The CMake project adds `core/` as a subdirectory and compiles the portable
`app/Sources/*.c` files plus two Android-specific files:

- `engine.c` — the machine owner: one pthread runs
  `s5l8900_run(..., 100000, ...)`, inputs are drained between chunks, frames are
  published at 60 Hz into an `ANativeWindow`, and checkpoints are written when
  the stop mode asks for one.
- `jni_bridge.c` — the `NativeEngine` JNI surface. Method names are part of the
  ABI (`Java_com_tayga16_s5landbox_nativebridge_NativeEngine_*`); renaming the
  Kotlin package or object breaks the link at run time.

The APK is built with 16 KiB page alignment
(`-Wl,-z,max-page-size=16384`), as current Android devices require.

## Runtime scheduling

The guest runs on one native interpreter thread. On Android 12+ devices that
expose the [NDK Performance Hint API](https://developer.android.com/ndk/reference/group/a-performance-hint),
the worker opens one ADPF session with a 16.67 ms target and reports each active
60 Hz work interval. Android can use that measured workload to adjust CPU
scheduling; the emulator does not create extra workers or pin itself to a core.
The API is loaded dynamically, so Android 8–11 and devices without the API
retain the existing behavior.

The hint is best-effort, not a promise of a specific clock or frame rate, and
has not been measured on a Helio G99 phone. Guest graphics remain CPU-side; this
does not submit iPhone MBX or QuartzCore work to the Mali GPU. A host GPU
renderer is a separate compatibility project.

## Testing notes

- On an emulator without Bluetooth or audio, the guest still boots; audio output
  attaches to the default media route.
- The interpreter remains the performance limit. The Android performance hint
  improves scheduling only; there is no JIT on Android.
- Import and snapshot operations block their background thread for seconds to
  minutes on a phone, so the UI always shows progress and keeps the screen on
  during an import.
- If the app is killed mid-import, the partial artefacts are simply replaced on
  the next attempt. If it is killed mid-provisioning, the next boot recognizes
  the short writable image and makes it again.

## Licensing

The portable core and app sources remain under the repository's MIT licence.
The Android shell is new code under the same terms.
