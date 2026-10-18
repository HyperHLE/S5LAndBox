# Guest Files

Open **Machine Settings → Files** while the guest is running.

- **Media** browses `/var/mobile/Media` through the stock, paired AFC service. It does not require a jailbreak.
- **Filesystem** browses the guest root directory. New jailbreaks provision this service automatically. For an older jailbreak, shut down the machine and run Jailbreak again with Packages setup enabled to add the service.
- Tap a folder to open it or a regular file to preview it. Long-press an item to export, rename, or delete it. Use **+** to import a file or create a folder.

Imports and exports are limited to regular files up to 512 MB. Existing destination names are refused. Deletion is confirmed and nonrecursive: a directory must be empty. Symbolic links and special files are shown but are not exported. System file changes can make the guest unbootable; there is no undo.

## Transport and provisioning

All browsing and file changes use AFC over the emulated USB transport, with bounded requests, cancellation, and fresh connections for each operation. The running disk image is never modified directly. Save/resume invalidates old connections through the existing USB lifecycle.

The root endpoint uses the guest firmware's existing `/usr/libexec/afcd --launchd -d /`, configured by an owned launchd plist. Its socket listens only on guest loopback port 62080. No Apple executable, third-party AFC2 package, host listener, or physical-device service is bundled or installed. Like AFC2, this grants root filesystem access; other processes inside the jailbroken guest can access its loopback endpoint.

The stock IPA staging client retains its original restricted path policy. The file browser explicitly opts into the broader AFC path capability. Responses, paths, filenames, directory sizes, transfer sizes, and file types are bounded and validated.

Uploads use a uniquely named `.s5lbox-import-…` temporary file and rename only after transfer completion and a destination check. A disconnection can leave that named temporary file in the guest; it can be removed through Files. Preview/export copies live in an app-owned cache directory and are removed when the browser closes. Files being modified concurrently by guest apps may change during a transfer; stop those apps before editing their files.
