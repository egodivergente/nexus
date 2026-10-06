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
