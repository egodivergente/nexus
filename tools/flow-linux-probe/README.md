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
