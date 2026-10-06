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
