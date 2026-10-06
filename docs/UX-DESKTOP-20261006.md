# Desktop UX verification - 2026-10-06

Scope: the Windows application installed on the user's PC, not Android.

The recovered GitHub branch already contains the reviewed desktop changes:
serialized snapshot saves, warning before closing with unsaved changes,
loopback session recovery after transport failure, named dialogs and accessible
prompt-limit feedback. Comparison with local commit 4bbae4a confirms identical
app.js, local-bridge.js and test-app-optimization.py ignoring line endings.
studio.js differs only by a trailing blank line. No source overwrite is needed.

PASS: 20 engine tests; isolated Edge tests for save order, preserved prompt and
attachments after errors, duplicate-message prevention, accessible dialog name,
prompt limit validation, and layouts at 1360 and 760 pixels.
These tests were run on the installed workspace during this UX task.

NO PROBADO: paid generation, each provider's complete download flow, actual
screen-reader use, and all catalogue variants. Synthetic tests do not prove
provider integration. No paid operations or Android changes were performed.

No compilation or packaging was necessary: the host serves frontend source.
Existing application windows must reload to load changed frontend scripts.
This branch preserves the recovery branch and records the verified UX scope.

## Second PC review

Priority defect: an asynchronous quote could enable generation after the user
changed the model or draft, associating the old estimate with new settings.
Quote requests now capture an isolated snapshot and reject responses whose
model/draft identity no longer matches. Only the latest request can update cost.
Invalid forms are rejected locally before asking for a quote. Expired quotes
clear automatically and block generation. Quote completion no longer replaces
another screen with the canvas after navigation.

PASS: stale quote rejection, isolated parameter snapshot, navigation preserved,
out-of-order replies, expiry blocking and no quote call for an invalid prompt.
PASS: previous save/draft/accessibility regressions and 20 engine tests.
PASS: 1360/760 pixel viewport overflow checks; 760px screenshot visually reviewed.
No extra confirmation step was introduced. No new dependency was added.

NO PROBADO: live paid generation/download, provider availability, screen reader,
complete keyboard audit, measured memory/CPU and every model configuration.
No build was required for these source-served JavaScript changes.
Other ongoing local Flow changes were preserved and excluded from this commit.

## Assistant correction after user-reported failures

The earlier verification did not cover Enter in the Nexus assistant. This was
a real missing keyboard handler, now fixed: Enter sends, Shift+Enter inserts a
newline, IME composition and key repeat do not submit. Icon buttons have explicit
button types. Focus returns to the draft after a reply/error; asynchronous reply
completion no longer forces another screen back into the assistant.

Local bridge recovery now renews a rejected session once on HTTP 403 only.
The host rejects that response before dispatch, so no executed request is
retried. A transport disconnect is never automatically replayed. Errors explain
uncertain delivery, remain visible in the assistant and preserve its draft.
Connection checking does not resend the message.

The assistant layout now uses the available flex height instead of a fixed
viewport subtraction: compact header, grouped mode controls, bounded reading
width, centered empty composer, visible send control, and contextual image review.
The runtime is still Edge-backed; this is not a native framework migration.

PASS: test-assistant-reliability.py drives actual keyboard/click events in Edge,
checks 1536/1280/760/480px layouts, kills and restarts its own isolated loopback
host and verifies real fetch failure plus a real 403 after token rotation.
Reply text is a fixture, not a model response. No CSP bypass is used.
PASS: previous optimization suite. Desktop and narrow screenshots inspected.
PASS: live installed host /health and authenticated assistant snapshot returned
success with offline=false and us.anthropic.claude-sonnet-4-6 configured.
NO PROBADO: live model inference, paid generation, all causes of historical fetch
errors, screen reader and stability over extended use. No build/restart of the
user's active instance was performed; existing windows require a frontend reload.

## Visible account balances

Added a persistent balance strip below the main header in every view. Configured
providers show their own units, reading time and a manual refresh control. Initial
loading queries only read-only balance endpoints, once per window; rendering does
not duplicate requests. Unsupported providers show unavailable, never a fake zero.
On refresh failure, an earlier value remains explicitly marked as the last datum.
Settings account queries use the same parser and presentation.

Live read-only checks on 2026-10-06 succeeded for Atlas, Runway and Crun. Atlas
returns available.value plus available.currency, not a scalar available field;
this nested response is now parsed correctly. No account identifiers or tokens
were copied into this report or the source. Flow balance remains unsupported.

PASS: tests cover nested/string amount, true zero, failure/unavailable, a single
strip across rerenders, and visible responsive balances at 1360/760/480px.
PASS: assistant keyboard and loopback-restart regression suite after this change.
No compilation/package or paid generation was performed. No claim is made about
provider balances staying unchanged after the reading or about a combined total.
