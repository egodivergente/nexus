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
