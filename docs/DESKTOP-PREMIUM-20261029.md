# Nexus Desktop · 2026.10.29-desktop-premium.1

Base: `codex/desktop-modern-20261026` at `dfb9d561`, reconciled with the
AXELPC Desktop shortcut and installed modern edition. Work is isolated in
`codex/desktop-premium-20261029`; the dirty primary checkout and Android
premium work in another chat are preserved.

## Product behavior

- The existing NexusTen presentation is the shared token foundation. Type,
  spacing, radii, controls, focus rings, overlays, elevation and motion use the
  same scale across creation, assistant, catalog, history, library, settings and
  Corte. Native controls follow the selected light/dark theme. Large fonts and
  narrow layouts wrap controls instead of clipping them. Corte panels can
  scroll vertically on short laptop screens. Backdrop blur is removed from the
  active shell to reduce compositing work.
- Settings has a dedicated **Dispositivos** section. The **Dispositivos**
  shortcut in Assistant and Library opens it directly. It presents shared
  projects/gallery/chats through the existing Google Drive implementation and
  the existing encrypted direct link in one place. **Sincronizar todo** queues
  each configured channel once; navigation never starts a manual exchange.
- Last completed/successful exchanges survive restart. Pending work, missing
  originals and conflicts remain visible. Temporary network/token-endpoint
  failures request a retry; rejected Google grants request reconnection.
  Historical conflicts load from the local journal before any new exchange;
  review lists expand in blocks of 20 without sending resolution requests.
  Editing pairing fields is preserved during status refresh. No pairing,
  credential scope, signing identity or deletion policy is changed.

## Corrections and reasons for changed behavior

- A deferred Corte initialization could render after navigation, overwrite the
  current page and then dereference a removed video. Late editor renders now
  leave the current workspace and its draft intact.
- The compact navigation could mark the Crear label as prepared before its
  wrapper existed, leaving clipped raw text after repeated navigation. The
  prepared marker now follows creation of the accessible label wrapper.
- Opening a different installed edition used a PowerShell/WMI process query
  that timed out in the real startup error log and could terminate active work.
  A healthy existing Nexus host is now reused. After that instance closes,
  the updated shortcut starts the new edition. Recovery only probes loopback
  with a bounded timeout and never kills the previous agent, export or sync.
- New windows fit the Windows work area, including the taskbar, instead of
  always requesting an 840 px window. Edge no longer forces dark native
  controls over the user's light theme. The installer optionally accepts an
  explicit signed Python runtime path when Python is absent from PATH.
- Cancelled agent turns use the existing callback completion event instead of
  an idle 250 ms timer. Telemetry does not rewrite unchanged labels, hidden
  windows skip balance decoration, and NexusTen temporarily disconnects its
  observer while applying its own presentation changes.
- Packaging uses the running Python interpreter and the same version constant
  as the application. No dependencies were added. JVM sources/runtime behavior
  are unchanged; all 15 runtime JARs were recovered from the active installed
  package and their SHA-256 equality checked before packaging.

## Verification

Run on AXELPC with the installed Python 3.12 and real Microsoft Edge. Browser
regressions use disposable profiles, block external/paid requests and never
use production data as a fixture:

- `test-premium-workspace.py`: all seven views, light/dark, 1280/1920/768 px,
  200% text, keyboard focus, draft navigation, failed save/retry, accessible
  dialogs, reduced motion and late Corte initialization.
- `test-nexus-ten.py`: catalog, creation, assistant, history, settings and
  responsive regressions, including the new sixth settings category.
- `test-continuity-hub.py`, `test-drive-sync.py` and
  `test-device-continuity.py`: channel state, duplicate activation, refresh
  stability, authorization/network errors, encryption and preserved versions.
- `test-host-lifecycle.py`: seven launch/recovery/work-area tests.
- `test-desktop-local-files.py`: authenticated imports, range reads, local
  actions and scene/cut preservation; the explicitly opt-in physical 4 GiB
  copy is omitted. The 4 GiB interface contract remains unchanged.
- Node suites: turn cancellation recovery, chat continuity and catalog parity.
- `verify-corte-source.py`: 44 locked Corte source files match their manifest.
- `test-modern-install.py`: the packaged version, all seven sections, actual
  JVM tools, embedded editor chat and attachment access in an isolated profile.

Window/idle measurements are diagnostic observations on AXELPC, not an FPS or
battery benchmark on the physical EliteBook. Paid provider generations and
simultaneous editing on all three devices are outside the verified boundary.

## Real device continuity boundary

Read-only checks on 29 October found S25 Ultra and Pixel 8 Pro reachable and
running Nexus Agent. Both use the same Drive workspace as Windows; Windows has
an existing Google refresh token and direct pairing key. At that snapshot,
S25 was synchronizing; Pixel reported 312 conflicts and 104 missing originals.
Windows had 308 journal conflicts, four projects, 2,069 indexed assets and
1,670 local asset references whose original files exist. These counts do not
prove that Windows has each specific original reported missing by Pixel.
These are preserved for review. Connected configuration is not proof that all
devices have identical data or all originals are recovered. This desktop task
does not replace either Android installation or resolve distinct edits blindly.

Private profile backups, runtime evidence and screenshots stay outside Git.
The installed code is verified against its generated SHA-256 manifest.
