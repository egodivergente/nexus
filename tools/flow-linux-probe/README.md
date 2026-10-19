# ASUS Linux Flow probe (2026-10-06)

Scope: browser feasibility only, not a deployed Android Flow connector.

Host identity verified by existing SSH: ASUSVIDAL. Existing PC bridge untouched.
Installed Debian binaries using apt, without upgrades/removals or service restarts:
Chromium 154.0.8037.57, Xvfb, python3-venv and dependencies (414 MB).
Isolated runtime: `~/.local/share/nexus-flow-probe`, private fresh browser profile.
Python dependencies: Playwright 1.61.0, greenlet 3.5.6, pyee 13.0.1,
typing-extensions 4.16.0. No Google cookies or credentials copied.

## Evidence

- PASS: sandboxed headed Chromium runs on Xvfb without a desktop or monitor.
- PASS: Flow public page responds HTTP 200, redirects to `/about`, renders
  visibly and presents Sign in / Create with Google Flow controls.
- PASS: second public-page run completed in 27.466 s, peak 587.3 MiB,
  16.265 CPU seconds, zero swap in the job. Screenshot inspected locally.
- FAIL: first screenshot timed out (30 s); initial job peaked at 1.1 GiB.
  Revised probe blocks presentation media and disables screenshot animations.
- FAIL: initial sign-in selector required a visible link, but the header
  Sign in link was hidden. The probe now reads the page-provided href and
  follows it only after checking HTTPS and accounts.google.com.
- PASS: Google login form rendered at `/v3/signin/identifier`, title
  `Sign in - Google Accounts`, with Next and Forgot email controls.
  Final run: 20.841 s, peak 497.6 MiB, zero swap. No credentials entered.
  Authenticated workspace testing is blocked on user sign-in.
- PASS: pip dry-run resolves gflow-cli 0.82.1 for Linux/Python 3.13 using
  binary wheels only. This is dependency resolution, not bridge execution.
- NO PROBADO: authenticated Flow workspace, session renewal, generation,
  recovery/download, mobile connector and operation with the PC off.

No generation, project creation, credentials entry or consent acceptance.
No public listening ports, persistent services, APK changes or packaging.
Temporary systemd units collect on exit and kill their child processes.

## Repeat

Run on ASUS with current resource availability checked first:

```sh
systemd-run --user --wait --pipe --collect \
  -p MemoryMax=1200M -p CPUQuota=100% -p Nice=10 -p RuntimeMaxSec=100 \
  xvfb-run -a ~/.local/share/nexus-flow-probe/venv/bin/python \
  ~/.local/share/nexus-flow-probe/probe.py
```

Optional `--signin` follows the page-provided Sign in URL to Google's login
page; it never enters credentials. Reports and screenshots stay in the
private runtime directory, not Git. Do not run against an interactive profile.

## Temporary interactive login

`login.py` starts ordinary sandboxed Chromium with the same isolated profile,
x11vnc and noVNC. Debian packages `x11vnc`, `novnc`, `websockify` and their
dependencies were installed (11.4 MB). No Google credentials are scripted.

- VNC binds only `127.0.0.1:5909`; IPv6 is explicitly disabled with `-no6`.
  Initial verification detected a wildcard IPv6 listener, stopped that run,
  corrected the flag, and verified the restarted listeners were loopback only.
- Web access binds `127.0.0.1:6099`. Existing authenticated SSH forwards it
  to `127.0.0.1:6199` on the user's Windows PC. No public endpoint.
- A random per-run VNC password is kept in mode-0600 files, outside Git.
- Run under a transient systemd user unit with `MemoryMax=1200M`,
  `CPUQuota=100%`, `Nice=10`, `RuntimeMaxSec=1200`, `TimeoutStopSec=10`.
- Native Chromium is not launched with automation flags or disabled sandbox.
- Interactive noVNC connection and visible Flow page verified in Codex.
  User sign-in and authenticated Flow operations remain pending.

Close only this task's session with `systemctl --user stop nexus-flow-login`.
Stop its Windows SSH forwarding process separately after user handoff ends.
Do not launch the automated probe while this profile is open interactively.

## Authenticated trial and restart failure

2026-10-06: the user completed Google login in the ASUS browser. PASS:
Flow displayed the existing project gallery and signed-in account. No project
was created, modified or generated in this check.

FAIL: after closing Chromium normally and reopening the same profile,
Flow initially showed a project-loading error. Reloading led to `/about`;
the account chooser explicitly marked the account Signed out. The cause is
not established. This disproves persistent unattended access in this trial.
Do not advertise the ASUS connector as operational or ask for paid validation.

Installed gflow-cli 0.82.1 from binary wheels into the isolated venv;
`pip check` passed. `health.py` returns only a sanitized status, checks Flow
workspace readiness plus server-attested Google authentication, and refuses
to open a profile already held by Chromium. The busy case was tested PASS.
The connected path is NO PROBADO. `profile_diagnostics.py` reports only
persistence counts and preference flags, never cookie names or values.
No cookies, profile files, account identifiers or screenshots are in Git.

Next: user reauthentication is required. Investigate session persistence;
then validate read-only bridge operations before any APK integration or
generation. No additional public endpoint or permanent service was installed.

### Second login and persistence check

The user reauthenticated. PASS: existing projects visible, and refreshing the
same open Chromium window preserved access. Structural diagnostics counted
27 persistent Google/Flow cookies without reading/exporting their values.
After normal UI close, Chromium recorded `exit_type=Normal` and the count
remained 27. This rules out loss of all on-disk session data at that close.

FAIL: a subsequent isolated `health.py` run returned `auth_required` after
31.465 s (24.064 CPU seconds, peak 1 GiB, no job swap). After that browser
closed, the structural count was 10 and exit remained Normal. The cause of
the invalidation is not established; do not claim it is a user mistake,
memory pressure, cookie encryption or Google's automation checks without
further evidence. No third login was requested and no paid work was run.

Important compatibility finding: the ordinary interactive Chromium launch
does not specify a password-store backend, whereas Playwright/gflow launch
with `--password-store=basic`. This difference requires investigation but
does not alone explain the earlier ordinary-to-ordinary restart failure.
Do not weaken credential storage or modify/export the profile as a blind fix.
The health check's `busy` and `auth_required` paths are tested; `connected`
and the end-to-end Android bridge remain NO PROBADO.

## Explicitly authorized credential-based login

The user authorized credential-based automatic login on 2026-10-06. The
credential is outside Git, encrypted with `systemd-creds --user`, scoped to
the ASUS user, mode 0600 under a private directory, with a 24-hour expiry.
Plaintext is supplied through stdin at provisioning, not written to a file.
No credential value, account identifier, ciphertext or browser profile is
included in this repository. Do not renew its expiry without user intent.

`health.py --reauthenticate` loads it only on an explicit reauthentication
run. `auto_auth.py` allows the exact HTTPS Google login origin, one password
submission and a 15-minute durable cooldown. It stops on consent, rejection,
second-factor verification or unexpected destinations. A redirect to Flow
is not success: workspace and server session checks still have to pass.
No scheduler, permanent daemon or Android connector was enabled.

PASS: agent-operated ordinary Chromium login with the authorized credential
loaded the existing Flow project gallery without additional verification.
PASS: six unit tests cover origin restrictions, credential validation,
missing credentials, cooldown, rejection and second-factor handoff.
FAIL: native `LoadCredentialEncrypted` in the user unit returned
243/CREDENTIALS (host key permission). Explicit user-scoped systemd-creds
decryption works; the loader uses its supported interface, capturing plaintext
in memory only. It does not weaken key permissions or use plaintext storage.
FAIL: real unattended reauthentication returned `needs_user` in 37.113 s,
peak 1019.1 MiB, no job swap. Sanitized browser history confirmed Google's
`/signin/rejected` route. No repeated password submissions or paid work.
The rejection branch now returns `reason=browser_rejected` and is unit-tested.

Ordinary browser login is verified; unattended engine login is NOT operational.
The encrypted credential expires after 24 hours and changing the password
requires explicitly updating it. It does not grant extra Google privileges.

## Private Tailscale mobile recovery (2026-10-06)

Following user authorization, when Google blocks unattended reauthentication, access is restored via a private bounded remote recovery portal hosted on ASUS and reachable over Tailscale:

- `recovery.py`: FastAPI server binding `100.112.57.2:8899`, requiring exact Host header and Bearer/cookie token authentication (token generated locally with 32-byte entropy, mode 0600, outside Git).
- Sandboxed Xvfb Chromium launched with `--persistent` and CDP port 9229.
- `recovery.html`: Responsive recovery controls embedding noVNC through an authenticated WebSocket proxy (`/websockify`). HTTP/WebSocket traffic uses the encrypted Tailscale network, not TLS at the application layer. Allows user to complete Google sign-in directly in the ASUS Chromium instance.
- Bounded unit `nexus-flow-recovery.service`: `MemoryMax=1400M`, `CPUQuota=100%`, `Nice=10`, `UMask=0077`, `TimeoutStopSec=15`.
- Closing the remote view (`/lock`) disconnects the mobile client and clears session cookies, but preserves the running Chromium browser on ASUS so the bridge can reuse that authenticated session without restarting.
- PASS: 10 unit tests in `test_recovery.py` covering private routes, host/origin validation, token checks, body limits, lock/unlock persistence, expired sessions and traversal prevention.
- PASS: live desktop in-app browser connection over Tailscale on port 8899; physical S25 verification is pending.
- PASS: status check detects `auth_required` when Flow lands on `/about`. The `connected` branch is implemented but not validated in the current recovery session.
- No Google passwords, user credentials or profile data stored in Git.

### Recovery validation and Android entry point

- Fixed an unsolicited WebSocket `binary` subprotocol that current noVNC clients reject. Added both no-protocol and explicit-binary handshake regressions. Total: 12 recovery tests plus 6 authentication tests pass on ASUS.
- Live noVNC canvas renders Flow; closing and reconnecting the portal preserves browser supervisor PID 1150678. Account status correctly reports that sign-in is required. No paid generation was submitted.
- Startup readiness budget increased from 9 to 60 seconds so a slow initial browser launch is not prematurely reported as busy.
- Android Settings adds `Google Flow > Conectar Flow`, opening this private portal. Its pairing key is kept in existing encrypted storage, passed in a URL fragment, then removed from browser history by the portal. ADB-only debug pairing can provision the token without typing it on the phone.
- This is account recovery, not a Flow generation provider. `generation_enabled` remains false. Automatic job resumption and APK generation through ASUS remain unimplemented.
- PASS: `check_recovery_live.py` verified the running service rejects anonymous access, negotiates RFB without a WebSocket subprotocol, closes an already-connected socket after revocation, and invalidates its cookie.
- PASS: selector job `20261006T141333Z-ad51ac96` on the idle Pixel compiled Android 1.7.3-agent-preview and ran 217 tests (zero failures/errors). Collected archive integrity verified. Build duration: 13m32s.
- PASS: physical Pixel reaches ASUS over Tailscale. NO PROBADO: physical browser/app UI; the tool policy blocked the requested browser launch, so no alternate launch route was attempted.
- PASS: signing job `20261006T143211Z-7adddb31` used the original certificate on Windows with verified 5% CPU cap. Signed APK SHA-256: `da489a661778d037771b89855974b5403adf61f246faa68ed10bacf346b465c1`.
- PASS: update installed with `-r --user 0` on Pixel 8 Pro and S25; both report versionCode 21 / 1.7.3-agent-preview. No app data was cleared. Private recovery pairing returned `ok:true` on both; empty-token pairing was rejected on Pixel. S25 foreground application was not opened or changed.

### Mobile keyboard recovery

The remote desktop canvas does not request Android's keyboard when the user taps a Google password field. The portal now exposes a visible `Teclado` button that synchronously focuses noVNC's existing keyboard input during the user's tap, preserving its key forwarding and avoiding noVNC's touch-device detection gate. It does not collect passwords or introduce a text-submission endpoint.

- PASS: live desktop browser click focuses `noVNC_keyboardinput` inside the remote iframe.
- PASS: three Node tests cover synchronous focus, loading/unavailable input, and cleanup on remote close (`node --test tools/flow-linux-probe/test_recovery_keyboard.cjs`).
- Deployment: HTML-only update on ASUS, no browser/service restart and no APK rebuild. The mobile page must reload to receive the button; the remote Google page remains open.
- NO PROBADO: Android soft-keyboard appearance and password entry on the actual S25. The user is asked to verify this without the agent typing or submitting credentials.

### Agent knowledge correction (1.7.4)

The agent incorrectly treated the executable Nexus catalog as the universe of existing products. Fixed the system instruction and empty-search guidance. Google Flow now has a shared, source-attributed service description, including the private recovery entry point and the explicit absence of a Flow generation adapter in this APK. These facts are included even with a custom system prompt and in existing conversations; search results keep external-service metadata separate from executable models.

The offline planner explains this boundary without selecting a replacement provider or creating a paid plan. It checks the user's text, not attachment filenames. Six regressions cover mandatory knowledge, read-only search metadata, offline requests, ambiguous names, attachment filenames, and the LLM request contract with a scripted provider. They do not demonstrate the behavior of a real paid LLM.

Sources checked on 2026-10-06: [Google Flow video guide](https://support.google.com/flow/answer/16353334) and [image guide](https://support.google.com/flow/answer/16729550). No account-specific costs, model versions, limits or feature entitlement are inferred.

The build recipe uses Gradle's unique project abbreviation `:agent:test` for `:agent-core:test`; the selector currently rejects hyphens in task paths. The full core and Android unit suites are requested before assembling the APK. Job `20261006T145312Z-dbc58720` was deliberately stopped (143) before delivery to include the attachment-filename regression in a fresh selector snapshot.

- PASS: selector build `20261006T145906Z-509dca5d` completed on the screen-off Pixel in 17m 56s. Collected artifact and source manifests passed integrity verification. JUnit XML reports contain 217 app tests and 80 agent-core tests, including all six new regressions: zero failures, errors or skipped tests.
- PASS: signing job `20261006T152243Z-bf568d73` completed on the key's Windows custodian with a verified 5% CPU / 512 MB cap. The original certificate SHA-256 is `8b75db0fe0849019990f0d9d6426b95d85414f0585acac78a06c484b91cd7127`; signed APK SHA-256 is `4db67c1c1e72e21fd99b270e08f77cf4781fb37176cb2b0b2ddef10f842d4e37`.
- PASS: `install -r --user 0` succeeded on Pixel 8 Pro and S25 without clearing data. Package inspection and the read-only agent status provider both report `1.7.4-agent-preview`, versionCode 22. The S25 foreground activity remained unchanged. No app was brought to the foreground for this check.
- NO PROBADO: a real paid LLM response, fresh visual interaction in the APK, account authentication and Flow generation. The status provider responds but reports `catalogReady:false` before UI initialization; this is not evidence of a loaded catalog. No paid generation or LLM request was submitted.

### Automatic reuse of the authenticated session

The user completed mobile login and requested reuse of that session, not generation automation. The existing ASUS browser remains open with its private profile; no Google credential was entered or exported for this change.

- Fixed the status check: an open Flow project has a composer/workspace instead of the gallery's New project button. Both surfaces are now recognized, with visible controls and server-confirmed Google authentication required. A project URL alone, hidden controls or a failed Google response are not success. Checks no longer navigate blank tabs.
- The portal checks authentication automatically after connecting. An active session skips the remote viewer. During manual recovery it checks every six seconds, then hides/revokes only the remote view when authentication succeeds. Explicit `Abrir navegador` still opens the current workspace. Failures have bounded retries; requests have timeouts. The ten-minute remote-access lifetime is unchanged.
- PASS: 23 Python tests (17 recovery/session tests, 6 authentication tests) on ASUS, plus 9 Node portal tests locally. No Google account was accessed by unit tests.
- PASS: live status now returns `connected` for the user's existing project. Reloading the portal twice reaches `Google Flow conectado` automatically without credential entry. The protocol smoke test still rejects anonymous requests and revokes the remote socket/cookie on close.
- Deployment preserved Chromium PID 1150682. Only the portal main process was restarted (1150497 to 1182520) under a temporary runtime `KillMode=process` override, removed immediately afterward. `KillMode=control-group` was restored and verified; the service is active and the Google session remains connected. No APK compilation, packaging or installation was necessary.
- NO PROBADO: persistence after Chromium/ASUS restart and Google's future session expiry. No browser restart was performed because previous trials lost authentication. If Google requires verification again, manual recovery is still needed; there is no repeated automatic password submission or bypass of verification. At this historical checkpoint, Flow generation remained unimplemented and disabled.

### Direct Flow bridge source (2026-10-06)

The current source adds an explicit, private Flow generation bridge for the NEXUS Android app. It is intentionally narrower than a provider API: it operates only the single already-open authenticated Flow project through its visible UI, sends a text prompt only after a local confirmation, and leaves model selection, project settings and cost inside Flow.

- `GET /api/flow/status` reports whether the existing project exposes the observed composer controls.
- `POST /api/flow/jobs` requires explicit confirmation, limits prompt text to 4,000 characters locally, journals opaque idempotency metadata only, and never retries an uncertain click.
- Results are considered available only when a new, unambiguous Flow download control appears. The Android client copies the file to ALEXIA before marking the task complete.
- The bridge accepts only the ASUS Tailscale IP and its MagicDNS host. Tokens, prompts, browser cookies and Google credentials are never committed or emitted by the API.

PASS: mocked recovery/API tests cover authentication, host checks, body validation, idempotency and conservative download detection. The updated bridge was also staged and tested with the ASUS virtual environment, then deployed while preserving Chromium; authenticated live status reported the project open, an editable composer and generation readiness. NO PROBADO: Android delivery and a real paid Flow generation. A real generation must remain an explicit user-confirmed action.

## Vigilante de sesión (2026-10-19)

`flow_keepalive.py` se ejecuta cada 5 minutos con `nexus-flow-keepalive.timer` (unidad de
usuario). Solo se conecta por CDP de loopback al Chromium que ya está abierto; nunca abre otro
navegador, nunca escribe credenciales, nunca crea proyectos ni recarga un proyecto abierto.

- Recuerda el último proyecto abierto (`keepalive-state.json`, modo 0600, fuera de Git).
- Si Flow se queda en la galería o en blanco más de 10 minutos, vuelve a abrir ese proyecto.
- Si la página se queda en `/about` (sesión cerrada) más de 20 minutos, la deja en
  `about:blank` y marca `needs_login`. La portada pública satura la CPU del ASUS. Una página
  de `accounts.google.com` (inicio de sesión en curso) no se toca nunca.
- `nexus-flow-recovery.service.d/graceful-browser.conf` añade un `ExecStop` que cierra
  Chromium con `Browser.close` antes de que systemd mate la unidad (`TimeoutStopSec=45`), para
  que las cookies que Google rota queden guardadas. Antes, el último cierre registraba
  `exit_type=Crashed`.

**Reinicio de sesión con la contraseña guardada (autorizado por el usuario el 19-10-2026).**
Si la página sigue en `/about` más de 2 minutos, el vigilante hace **un único intento cada 6 horas**
con la contraseña que el usuario guardó en este Chromium (autocompletado). Solo pulsa la cuenta
guardada y «Siguiente»: nunca escribe ni lee la contraseña. Se detiene ante cualquier verificación
(`/challenge/` salvo `pwd`), consentimiento, rechazo (`/rejected`) o destino inesperado, y guarda el
motivo en `relogin_result`. El intento se registra antes de actuar, así que un fallo a mitad no
provoca reintentos en bucle. Tras un fallo, la página se deja en blanco; pasadas 6 horas se reabre
la portada para el siguiente intento. Si no hay contraseña guardada, el intento termina en
`needs_user` sin más efecto. Riesgos aceptados por el usuario: Google puede rechazarlo (ya ocurrió
una vez en 2026-10-06), y el perfil sin llavero del sistema guarda la contraseña con poca
protección frente a cualquiera que acceda como `vidal`. Conviene mantener activa la verificación en
dos pasos de la cuenta.

Validación: 12 pruebas unitarias en local y en el ASUS. Comprobación real: estado `project`,
temporizador activo y puente aún `connected`. **NO PROBADO en real**: reabrir el proyecto, el reinicio de
sesión, dejar la página en blanco y el cierre limpio. Comprobarlo exigiría tirar la sesión a propósito.
No reiniciar `nexus-flow-recovery` sin necesidad: reiniciarlo cierra Chromium.
