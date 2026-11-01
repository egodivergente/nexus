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

## Delivery verified 2026-11-01

- Source commit: `fc100bf6bf7f11bc9ff9a3e50cc234e071723076`.
- AWS CodeBuild: `vidal-android-build:8990e640-98b1-4b13-be0c-40848c0dcaad`, SUCCEEDED.
  Gradle unit tests and APK compilation ran in AWS; six focused tests passed,
  with zero failures, errors or skipped tests. Installed ARM64 native binaries
  were reused in AWS; the private signing identity stayed on PC.
- APK 1.9.139-agent-preview / vc175 installed as an update on SM-S938B.
  Installed SHA-256 matches the delivered APK:
  `532b55afd4091a3672e8fdd4c8ecb6715de5b4e55a6fac26a8fe1a2d354d733c`.
  Original signing certificate, 16 KiB alignment and all three ARM64 libraries
  from the actual vc173 APK were verified unchanged.
- The live catalog returns both new routes under `usr_byteplus-media`.
  The image route reports the official provider ID, adaptive ratio, 4–30 seconds,
  480p/720p/1080p, audio and a required single image. All six original BytePlus
  routes remain stored and the migration marker is persisted.
- Ten preference files, including encrypted credentials, drafts, appearance and
  catalog choices, have identical hashes before and after installation. Loaded
  history, asset and project counts match the original installation.
- The provider JSON changes during migration. An exact raw JSON equivalence
  claim is not made for its legacy portion: serialization also materializes
  defaults and normalizes duplicate parameters. Additive preservation of
  configured models/settings is covered by the focused migration tests.
- No paid generation or account activation was performed. Actual generation,
  account authorization for this model and a verified generation price remain
  untested; the app reports an unspecified tariff instead of inventing one.
