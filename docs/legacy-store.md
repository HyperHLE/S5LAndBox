# Legacy Store

Available in development builds through **IPA Library > Legacy Store**.
Search for an app, tap **Get**, then **Install** when the verified download
has been added to your library. Manual IPA import remains available.

IPA inspection accepts relative symbolic links to files within the same app
bundle, including legacy `CodeResources` links. Links stay intact for live and
powered-off installation; they are never created on the host filesystem.
Targets must exist with exact spelling, use paths without `.` or `..` components,
and resolve to a regular file within 16 links. Directory links, cycles, special
files and extraction through a link are rejected. This fixes archive acceptance,
not compatibility with an app's online service or every emulated feature.

The native browser uses the public [Legacy Store API](https://legacystore.app/api/),
not an embedded website. Catalog metadata comes from Legacy Store and IPA files
come from Internet Archive. S5LBox includes neither those IPAs nor account credentials.

## Compatibility

The catalog request uses the guest boot profile, not the host iPhone. This
branch supports iPhone1,2 / iPhone OS 3.1.3 / ARMv6 only. The target is an explicit
value passed into the browser so future boot profiles can supply their own
model, OS, architectures and device family without changing the API client.
This does not add or claim iOS 6 support.

Legacy Store selects one qualifying archived version per app. S5LBox also
checks the returned target, minimum OS, architectures, device family,
installation status and incompatibility reasons. Entries lacking required
size/checksum/compatibility information are omitted. Search returns at most
50 results; this endpoint has no pagination. Narrow the search if needed.

A real-device compatibility match is not an emulator compatibility guarantee.
Apps may fail to run or depend on unavailable services. Installation may require
AppSync in a jailbroken guest. Existing ZIP, CRC, plist, ARMv6 executable,
encryption and minimum-OS validation still applies before publication and installation.

## Download behavior

- One download at a time; it stays available through the download button when
  navigating away from its detail page. Installation is always a separate action.
- HTTPS only, restricted resolver/archive hosts and bounded redirects; normal
  TLS certificate verification remains enabled.
- Streaming writes, bounded sizes, exact byte count and archive SHA-1 verification
  against API metadata. SHA-1 here detects transfer corruption, not publisher trust.
- An atomic library import only after validation, without overwriting existing IPAs.
- Cancel during transfer, or retry a failure from the beginning. Validation and
  publication finish as a noncancellable transaction. Keep S5LBox open; background
  transfer completion and byte-range resume are not promised.
- Temporary attempts are removed after failure/cancellation/completion; abandoned
  attempts are cleaned when the download service next opens. User library files
  are never removed by that cleanup.
- Debounced search, five-minute bounded in-memory catalog caching, stale-result
  fallback after a failed refresh, and a retry cooldown avoid rapid repeat requests.

Synthetic Foundation tests exercise metadata filtering, malformed responses,
URL policy, size/checksum failures, cancellation, retry, cache reuse, stale-search
suppression, publication refusal and preservation of existing files. The iOS
workflow also compile-checks the UI against the iOS 13 API baseline.
