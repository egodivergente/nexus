# Nexus Desktop · 2026.10.29-desktop-premium.5

## Shared project folders and native classification · desktop-premium.5

The gallery now displays the APK's actual projects as folders, using their
stable IDs and names from the shared index. All files, unassigned files and
each project have separate views and deduplicated counts. Empty archived
projects retain their metadata; archived projects containing files remain
accessible. Existing local collections remain explicitly labelled as belonging
to this PC and never substitute for shared project folders.

Each file retains its project memberships and native classification: origin
(Nexus/generated, imported, edited, shared), role, scene, shot, characters and
tags. The same original can belong to two projects without losing either
membership. Combined filters must match one membership; the role from project
A cannot satisfy a filter in project B. An explicit unassignment overrides the
historical generation's project, matching Android. Source defaults and edited
precedence follow Android LibraryOrigin. No filename or directory heuristic is
used to invent a project or hierarchy.

Browsing, filtering and viewing do not move files or rewrite the shared index.
Viewer variant navigation respects the selected project and classification.
Imports capture the gallery's project before starting the transfer, including
an explicit null for Unassigned, independently of the assistant's active
project. Saved work points retain project and classification filters with
validated values. No generation/provider behavior or dependencies change.

Physical read-only checks found APK 160 on S25 Ultra and APK 161 on Pixel 8
Pro, each with six project IDs. During this task Drive applied the newer index to Windows,
which then contained six projects as well. The first completed exchange also
verified all 283 protected output records against Drive checksum metadata.
This does not imply that every historical original is available or every
conflict is resolved; those statuses remain visible separately.
A final read-only comparison of Windows and Pixel matched all 2,611 asset IDs
and their project, role, source, scene, shot, character, tag and parent metadata.
All six project names/archive flags and 1,326 retired records matched too; local
URIs differ by device and are intentionally excluded from this comparison.

Validation: 53 library-model checks, nine backend projection tests and 15 work
point tests pass. The 43-check real-media Edge acceptance covers multi-project aliases,
explicit unassignment, archived/empty folders, cross-scope filters, rename by
stable ID, resume filters and real file chooser imports. Dark 1440 px and light
640 px with 150% text pass without horizontal overflow. The existing 69-check
library/resume workflow also passes. Private audit data and screenshots stay
outside Git; Android data and installations are not changed.

## Complete library and explicit resume · desktop-premium.4

The library now reads the shared AssetIndex as well as every individual history
output. It includes indexed originals without a Desktop generation record,
preserves multi-output groups, and merges aliases only through exact URI,
proven local paths or verified content identity. Retired assets and existing
per-file favourites, hides and collection membership remain effective. A file
belonging to two generations remains accessible from both groups. Importing an
asset no longer invents a generation job. PDF and other binary documents offer
an original download instead of being fetched as text or embedded unsafely.

The projection is read only. Browser state and provider payloads stay intact.
The index has its own provenance resolver so archive protection cannot hold its
read lock; protection metadata refreshes independently of the large index and
journal. This corrects contention observed during the real first archive pass.
Fresh outputs remain visible while the index refreshes; unchanged snapshots do
not rerender the grid. Refresh preserves search selection and scroll position.
The canvas, recent tray and stage also request local bounded previews, fixing
requests to expired external URLs during intermediate render passes. The two
preview workers are shared across history and indexed assets.

A single background worker protects available completed originals in permanent
local media. Copies are SHA-256 checked, fsynced and atomically published; failed
metadata writes roll back the visible record. Original URLs and user files are
retained. New requested asset IDs include the output identity and content SHA so a
changed output cannot overwrite a previous registered original. Registration
uses the canonical ID actually persisted by the engine: an existing exact URI,
including a retired asset, keeps its original ID and metadata. Partial
registration and metadata failures cannot mark uncommitted identities as saved. Historical bootstrap
uses existing local originals; explicit retries may retrieve remote outputs.
Local registration and cloud verification are separate states in Dispositivos.
A cloud copy counts only after the locally captured SHA-256 and byte size match
Drive server checksum metadata for its blob; journal success alone is not proof.
Unavailable or conflicting checksum metadata leaves that original pending.
The Google callback now remains available for 30 minutes, allowing time for
manual consent without losing the local callback after five minutes. Reopening
a pending authorization reuses its live callback instead of creating a second
flow or locking reconnection until the timeout.

**Guardar trabajo** records an immutable, explicit work point with project,
view, model, prompt, controls, original references and editor/library context.
Receiving a work point never applies it. Explicit resume preserves a conflicting
local draft and project context as a private recoverable copy; undo restores it.
Only explicitly shared work points and verified reference blobs are advertised
through the encrypted direct link's workspace-v1 capability. Older peers are
reported as incompatible with full work-point resume.

The existing Android Agent bridge can prepare model, prompt and parameters on
an accessible Pixel 8 Pro or S25 Ultra without generating. Preparing is inert;
opening is a separate action and first preserves the mobile draft privately on
PC. Android 1.9.122-agent-preview / versionCode 158 does not consume project,
media references or Corte context through this bridge. The UI states that
boundary. No Android sources or installations are changed by this release.

Validation uses disposable profiles with copies of real images/video, native
Windows Edge, and physical read/prepare/readback/discard on both Android phones.
Tests cover complete output coverage, alias/group handling, local original
integrity after cache deletion, metadata rollback, no fabricated jobs, explicit
resume/undo, no generation operations and no external browser requests. New
Dispositivos sections were also checked in light/dark at 1280 px and 640 px with
150% text. Current PC read-only audit represented every one of 1,130 visible
indexed asset records and all 370 history outputs; proven aliases normalized
them to 897 distinct files before user display preferences. A cold projection
fell from 52.55 s to 13.14 s on AXELPC; cached projection measured 0.019 s. These
are diagnostic timings, not an EliteBook performance claim.

Release checks: the integrated real-media workflow passed 69 checks, the
legacy thumbnail/resource suite passed 47, Drive passed 25, work-point backend
13, Android adapter 10, direct continuity 28, the pure library model 26 and
original-state rendering 5, and canonical original registration 9. The seven-view layout matrix completed its layout
checks; the updated canonical selection/focus/save/contrast suite passed all 36
focused checks. Physical Android verification covered prepare/readback/discard;
opening the mobile canvas was mock-tested only to avoid replacing active work.
The real first local archive pass protected 283 output references in 264 unique
files. A separate complete SHA-256/size readback found no mismatch. The remaining
87 historical output references still needed an available original; Drive
authorization and full cross-device convergence were not inferred from this pass.

## Gallery correction · desktop-premium.2

The first delivery's gallery test used a tiny fixture to check layout. It did
not validate the production gallery's sources or video frames. The user's
screenshot exposed empty videos and unavailable historical images.

Video cards now show real cached frames before hover. Images use bounded
thumbnails from existing originals. A read-only resolver follows explicit
generation identity and immutable journal revisions to recover local originals
behind stale historical URLs; compatible historical variants must agree in
SHA-256. Multi-result order comes from coherent historical output arrays,
never filenames, prompts or approximate timestamps. The job/index records and
original URLs remain unchanged.

Thumbnail processing is limited to two workers, one FFmpeg decoder thread per
video, and visible cards. Hover playback releases its source on leave or close.
The viewer and download actions use the same recovered full original rather
than retrying an expired URL. Missing sources have a scoped retry state.

No new dependencies or generations are used. The preview cache is private and
derived; original files are never overwritten. Remote fallback only retrieves
registered outputs or reads an existing provider prediction, with bounded
download sizes and no automatic generation retry.

Real production validation decoded the first 36 thumbnails successfully using
local originals with zero network requests. Across the whole visual history,
205 of 271 first-result thumbnails decoded from locally available originals.
The full history has 370 output
references: 54 were already local, 229 more resolve to proven local originals,
and 87 had no proven local correspondence at the audit snapshot. This last
count does not prove remote originals are irrecoverable.

`test-gallery-previews.py` uses isolated copies of a real 378 KB image and
5.3 MB video, checking real decoded pixels, frame visibility before hover,
full-original playback/download, cache/limits/error states and preserved data.
`test-gallery-sources.py` covers provenance, coherent historical ordinal
recovery, SHA disagreement and malformed/unrelated records: 15 tests PASS.
The real-media, HTTP and resource regression suite passed 46 checks, including
rejection/eviction of corrupt downloaded cache copies while preserving user
originals. A pending normal save completes before preview requests so newly
imported results are visible to the resolver.

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
