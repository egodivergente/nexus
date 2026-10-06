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

