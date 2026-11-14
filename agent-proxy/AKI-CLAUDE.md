# Claude 5.5 through AKI

`aki_proxy.py` supports the existing GPT-5.6 Sol connection and these exact AKI IDs:

| Model | AKI ID | Default effort |
|---|---|---|
| Haiku 5.5 | `global.anthropic.claude-haiku-5-5` | `medium` |
| Sonnet 5.5 | `global.anthropic.claude-sonnet-5-5` | `high` |
| Opus 5.5 | `global.anthropic.claude-opus-5-5` | `medium` |

All three accept `low`, `medium`, `high`, `xhigh`, and `max`. The request gets a
temporary private AKI profile containing the exact model, effort, and enabled
reasoning. Profiles are removed after the call. Unknown-model fallback output
from older AKI binaries is rejected; it is never presented as the selected model.

Add each required model ID to the existing private `allowed_models` configuration.
An ID with an `@effort` suffix selects that level, e.g.
`global.anthropic.claude-opus-5-5@max`. Include the suffixed IDs in the allowlist
as well. The suffix is removed before calling AKI. `akiEffort` in Converse, or
`reasoning_effort` in Chat Completions, overrides the suffix. `minimal` is rejected
because these Claude models do not offer that effort level.

Existing `/v1/converse` clients remain supported. Custom Nexus brains can use
`http://127.0.0.1:8787/v1` with the `OPENAI_CHAT` protocol and streaming disabled.
Authenticated `GET /v1/models` lists the configured model/effort choices;
`POST /v1/chat/completions` translates text, embedded images, tool declarations,
tool calls, and tool results through the same authenticated Converse pipeline.
It uses the existing AKI account, not direct AWS credentials or a separate API key.

On Nexus versions whose effort chips only offer low/medium/high, leave effort on
auto and choose the model ending in `@xhigh` or `@max` in the brain's model field.
Explicit effort chips take precedence over an effort suffix.

## S25 deployment verified 2026-11-14

Nexus Agent `1.9.178-agent-preview` (`versionCode 214`) has three custom brains:
`aki-haiku-55`, `aki-sonnet-55`, and `aki-opus-55`. Each has its base model plus all
five effort variants. BytePlus and other existing settings are retained.
The bridge remains local to the S25 and uses its newer AKI binary, which recognizes
all three model IDs. The older ASUS AKI binary does not recognize those IDs and was
not used for Claude calls.

Validation: 17 focused proxy/media/Claude tests passed. All 15 model/effort
combinations were checked against the generated AKI profile. Live Chat Completions
requests returned OK for Haiku/low, Sonnet/high, and Opus/max. The authenticated
model endpoint returned 19 entries (Sol, three default Claude models, 15 variants).
No Android rebuild or installation was needed.

References:
- https://platform.claude.com/docs/en/build-with-claude/effort
- https://platform.claude.com/docs/en/models/haiku-5-5/overview
