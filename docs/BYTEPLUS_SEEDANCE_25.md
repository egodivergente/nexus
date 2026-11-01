# BytePlus Seedance 2.5

Adds `dreamina-seedance-2-5-260628` to the native BytePlus preset as text-to-video
and first-frame image-to-video. Duration is 4–30 seconds; resolutions are 480p,
720p and 1080p. First-frame requests explicitly send `role: first_frame` and
restrict ratio to `adaptive`. Audio and watermark retain their existing controls.
Automatic duration, last-frame, omni references, video/audio references, MOV
and draft/final workflows are not exposed by these two routes.

Source: [official ModelArk tutorial](https://docs.byteplus.com/en/docs/ModelArk/seedance-2-5),
checked 2026-11-01. No speculative price is supplied and existing confirmation
before paid submission remains in effect.

Existing installations run a one-time additive migration. Existing provider
settings, credentials and edited models are retained; removed legacy models are
not restored. An already-deleted BytePlus provider stays deleted. Deleting the
new routes after migration does not cause them to return on restart.

The change is based on vc173, the version installed on the S25, and uses vc175
(1.9.139-agent-preview) because vc174 is already assigned on another branch.
Storage changes from that separate branch are not part of this update.

Focused tests cover JSON types, official model and task paths, first-frame role,
ratio/duration/input validation, additive migration preservation/idempotence,
and asynchronous success/failure parsing. No paid generation is authorized as a test.
