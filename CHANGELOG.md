# Changelog

## 1.0.0 — 2026-10-15

The first public release of S5LBox: the iPhone 3G's iPhone OS 3.1.3 running
inside an iOS app. This release is experimental; compatibility and performance
are still being improved.

### Firmware and first-run setup

- Native first-run setup with progress, cancellation, errors and retry paths.
- Download the supported IPSW directly from Apple using metadata from ipsw.me,
  with SHA-256 verification before import.
- Import an existing IPSW as an alternative. Selection is handled immediately,
  without reopening Settings or choosing the file a second time.
- Automatically identify iPhone1,2 / iPhone OS 3.1.3 / build 7E18, resolve its
  publicly documented keys, decrypt and extract the required components, and
  validate them before preparing a machine.
- Keep firmware details and manual overrides in Advanced settings. Skip setup
  once firmware is configured. No Apple firmware is bundled.

### Machines, storage and controls

- Create multiple independent machines, with long-press actions to rename,
  duplicate or delete them.
- Save and resume running sessions, manage snapshots, or shut down for a fresh
  boot. Keep machine files accessible through the Files app.
- Use an 8 GiB virtual disk for new machines by default, with 2 and 4 GiB
  alternatives under Advanced. Sparse storage avoids allocating the full
  capacity immediately; guest data and snapshots still use real storage.
- Modern emulator toolbar and a separate device-controls sheet for Home,
  sleep/wake, silent mode, volume, pause, restart and Save & close.
- Separate app settings from running-machine settings; keep the legacy
  interface available in Advanced.
- Pause in the background by default. Provide Force Power Off for guests that
  cannot complete an ordinary shutdown, including during boot.
- Use guest shutdown confirmation before restarting a running machine. Forced
  power-off remains an unclean stop and can lose unsaved work.

### Guest apps and jailbreak

- A Files-visible IPAs folder and in-app import, with individual app selection
  and real IPA icons, including supported legacy optimized PNG artwork.
- Install apps while the guest is running through virtual USB, pairing, AFC
  and the guest's installation_proxy service—not by editing its live disk.
- Installation progress, cancellation, guest verification errors and
  per-machine pairing identities.
- Guided guest jailbreak with Cydia, centered preparation progress and
  automatic entry into the machine afterward.
- First-boot setup performs a respring. The completion notice explains that
  Cydia may appear afterward and Packages may need a guest reboot.
- Packages helper setup is included with jailbreak by default, with an
  Advanced opt-out and a setup path for existing jailbroken machines.

### Built-in Packages

- Native package browsing, categories, search, sources, installed packages
  and available updates.
- Download and process repository indexes on the host, with dependency
  planning, conflict checks and package downloads outside the emulated CPU.
- Install, update and remove packages using the running guest's own dpkg and
  maintainer scripts. Unsupported dependency plans are refused rather than
  silently applied.
- Handle supported package finish requests for respring or reboot after
  changes, with progress and connection-recovery feedback.
- First-use helper setup and first-boot waiting are integrated into Packages;
  missing setup is not treated as a successful connection.

### Emulation, networking and audio

- ARMv6 guest execution with a no-JIT shipping path; no host jailbreak or JIT
  activation is required to run the emulator.
- Experimental MBX graphics enabled by default for new machines, with the
  existing software-rendering configuration retained for machines that use it.
- Guest internet access enabled by default for new machines, using the host's
  network connection. Includes guest packet-transfer and repository-processing
  improvements developed before this release.
- Experimental audio playback and microphone input, guest digital volume/mute,
  host audio-session handling and saved audio-device state.
- Microphone access enabled by default subject to iOS permission, with an
  Advanced opt-out. Declining permission does not block setup or boot.
- A firmware-specific software-decoder compatibility path for supported audio
  playback; this is not emulation of the missing audio accelerator.

### Reliability fixes included in 1.0.0

- Fix virtual USB reconnection after Save & close for both Packages and live
  IPA installation. Wake a sleeping guest for pending service work, and detect
  controller resets instead of mistaking cleared interrupts for a completed
  handshake.
- Preserve deadlines for requests waiting for a connection, release canceled
  requests, and discard stale streams without replaying in-flight installs.
- Improve jailbreak/helper first-boot recovery, stale filesystem metadata
  handling and protection of referenced disk blocks during supported stopped
  maintenance operations.
- Preserve sparse disk allocation during maintenance copies and validate disk
  geometry and firmware identity before applying supported compatibility fixes.
- Fix firmware picker/import state handling, setup progress presentation,
  retained setup state and the separation of everyday and Advanced controls.
- Include regression coverage for snapshots, USB transport, installation,
  storage, firmware preparation, input, audio and package operations.

### Compatibility and known limitations

- Host requirement: an arm64 iOS device running iOS 13 or later. Physical-device
  validation has focused on iPhone 6s Plus with iOS 15.8.5; it does not establish
  identical behavior on every supported host version or device.
- Supported guest: iPhone 3G (iPhone1,2), iPhone OS 3.1.3, build 7E18. iOS 6 is
  not included in this release.
- Apps and tweaks must support this guest. Copy-protected IPAs cannot be
  installed; some otherwise compatible apps require AppSync inside the guest.
- Graphics, audio, input latency and performance remain experimental. Not all
  hardware, apps, tweaks, media formats or audio routes are supported, and
  original-device animation speed is not guaranteed.
- Packages is not a complete replacement for every Cydia/APT dependency plan.
  Unsupported plans may require Cydia. Guest scripts and initial jailbreak
  configuration can still take time; first boot may need a respring or reboot.
- Back up important machine data before experimenting. An interrupted
  installation can have an uncertain outcome; reconnecting does not undo it.

### Installation

Download **S5LBox.ipa** from the [1.0.0 release](https://github.com/j0shua-SYSON/S5LBox/releases/tag/v1.0.0)
and install it with your preferred IPA installer. The download is ad-hoc signed;
on stock iOS, your installer must apply a valid provisioning signature. Keep
several gigabytes free, open S5LBox, and choose **Download iPhone OS** or
**Import IPSW**. Keep the app open while firmware preparation finishes.

Apple firmware, machine disks, snapshots and test-device data are not part of
the release assets.
