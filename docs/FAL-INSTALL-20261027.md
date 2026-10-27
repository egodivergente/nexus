# Nexus 1.9.79 — Fal selection installed on S25

Installed on 2026-10-27, package com.vidadomu.nexus.agent, versionCode 113.
Source: 5f22cba81647593b4124604eceb32834f8552eb4. Integrates the Fal work from PR #12 with current UX branch through 7f7707405299894976be7de7dc7b6fe8af24767d. No Turbo or Starlight Fast in the new template.

Built on AXELPC with Gradle 8.9/JDK 17. Full native compilation completed; final assembleAgentPreview successful. Agent-core: 396 passed, 4 skipped, zero failures. No paid generation executed.
APK SHA256: a57b871fc2117a0e62da936b0fc1438f6d20b1c0d81781c362fb3532bdb12a3e.
Signing certificate SHA256: 8b75db0fe0849019990f0d9d6426b95d85414f0585acac78a06c484b91cd7127, matching the previous installation.

ADB identified SM-S938B. Installed using install -r --user 0 (no uninstall or data clear). Package reports 1.9.79-agent-preview / 113. Cold launch returned Status: ok; app process remains running.
Before/after database integrity: ok. All 611 generation rows and 480 output rows preserved, including full row contents. Shared preferences and conversation files byte-identical immediately after installation. Private backup retained outside Git; sync multimedia files were not altered.

Fal is available as the opt-in 'Fal · selección' template in the API-provider editor. Existing configured providers are preserved; no key or provider was silently activated. First-frame MiniMax only; see FAL-CURATED-20261027.md for pricing expiry and contract limitations. Full visual/end-to-end paid generation QA not performed.