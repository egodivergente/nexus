# NEXUS Studio desktop: UX revision 2026-10-06

Scope clarified by the user: NEXUS Studio and AXEL AI installed on this PC,
not their Android builds. No Android sources were changed.

## NEXUS changes

- Serialize state saves and capture each snapshot at enqueue time. Previously
  two requests could overlap; the new test observes one active save and verifies
  order. Failed saves do not prevent subsequent attempts.
- Warn before closing while a delayed, queued or failed save is outstanding.
- Clear the loopback session promise after a transport failure so the next
  request can reacquire it. No automatic retry of generation requests.
- Name dialogs for assistive technology and associate the prompt counter and
  over-limit state with the text input.

## Verification

PASS: isolated Edge flow for ordered saves, preserved prompt/attachments after
errors, no duplicate chat bubbles, accessible dialog name, prompt-limit guard,
and 1360/760px screenshots without horizontal overflow.
PASS: 20 existing engine tests. No provider generation was requested.
NO PROBADO: actual paid generation and download from each provider, screen-reader
session, full workflow with all catalogue variants. Test fixtures are not proof
that all provider integrations work.

No APK/EXE compilation or packaging was required: these changes are served from
the installed source. The existing application window must reload to see them.
GitHub destination is missing; a local commit is not proof of publication.

AXEL desktop source and its separate report are in
`C:/Users/User/Documents/ChatGPT/AXEL-AI`. Its backend and user data are unchanged.
