# Flow Studio: repeated reference uploads

On the S25, Nexus Agent 1.9.67-agent-preview (versionCode 101) recorded four
Flow Studio attempts as failed between 01:49 and 01:51 CEST on 2026-10-22.
Each had the error `No se pueden repetir referencias de Flow.` and no remote
job ID. Reference uploads reached ASUSVIDAL, but input validation rejected
the requests before submission to Google. No requests were replayed.

The upload service identifies files by their SHA-256 content. Different
picker URIs can therefore produce the same reference ID. Nexus now sends
each reference ID once, preserving first occurrence order. Validation still
rejects invalid files, unsupported roles, more than 12 distinct ingredients,
and identical Inicio/Fin frames. Restored queue references use the same
deduplication.

Flow Studio now also tracks failed repository records so their errors remain
visible after the snackbar disappears. Failed drafts remain editable.

The fix is based on the installed version's published branch
`feat/flow-agente-deslizar-20261021` (`dcd0bce`), preserving its changes.
The update is 1.9.68-agent-preview (versionCode 102), signed with the existing
Agent identity. The ASUS collector also recognizes current gallery media IDs and downloads originals through the tile menu, preserving the existing runtime additions. Credentials and stored jobs are retained.

Validation targets the Flow reference/queue and Studio JVM tests plus the
AgentPreview build on AXELPC. A new Google generation is outside this check;
successful generation and result delivery must not be inferred from the
reference fix or the bridge's connected status.

The previous two-day-old project DOM reported readiness even though its Google
session had expired. A read-only original download failed after redirects;
reloading the empty composer led to `/about`. Authentication must be restored
before a real download or generation can be verified. No login credentials
are exported and no requests are replayed. Gallery identity tests cover
project switches, reordered cards, duplicate tiles, and missing baseline
results; uncertain attribution remains an explicit error.

Validation completed on AXELPC: 36 Flow/Studio JVM tests and 39 bridge tests
passed, including a browser fixture downloading the original through the
current menu without selecting an upscale. AgentPreview assembled successfully.
The S25 was updated in place to vc102 with its existing signing certificate;
all 530 job IDs and the exact encrypted credential, assistant, brain-profile,
and Flow-queue preference hashes were preserved. The live bridge reports
`auth_required`; real Google download/generation remains unverified until login.
