# Android signing identities

Every Android build uses the same two established signing identities, regardless of which LLM, IDE, or machine starts Gradle. Gradle reads one user-local file, `~/.gradle/nexus-signing.properties`; APK-producing tasks verify both certificates before packaging and stop if the configuration is missing or does not match.

The app has two application IDs that are already installed with different certificates. They must keep their existing identities so Android can update each installation without losing its app data:

| Application ID | Variants | SHA-256 certificate |
| --- | --- | --- |
| `com.vidadomu.nexus` | `debug`, `offlineQa`, `release` | `287a0c42d49c672ef61fc789af44584f5cab9a54f0e1ae4aa1332af4a4bab0f9` |
| `com.vidadomu.nexus.agent` | `agentPreview` | `8b75db0fe0849019990f0d9d6426b95d85414f0585acac78a06c484b91cd7127` |

The fingerprints are public certificate identifiers. The private keystores and the local properties file are never committed. On a build machine, provision the existing stable keystore at `~/.android/debug.keystore` and the existing agent keystore at `~/.gradle/nexus-signing/agent-debug.keystore`, then create `~/.gradle/nexus-signing.properties` with these keys:

```properties
nexus.main.storeFile=${user.home}/.android/debug.keystore
nexus.main.storePassword=<existing keystore password>
nexus.main.keyAlias=<existing alias>
nexus.main.keyPassword=<existing key password>
nexus.agent.storeFile=${user.home}/.gradle/nexus-signing/agent-debug.keystore
nexus.agent.storePassword=<existing keystore password>
nexus.agent.keyAlias=<existing alias>
nexus.agent.keyPassword=<existing key password>
```

Keep that file and both keystores private to the account that runs Gradle. If an LLM runs Gradle under a different Windows/Linux user, give that user the same existing keystores and local config; do not generate a replacement key. A different local config path can be selected with `-PnexusSigningFile=<path>`.

GitHub Actions reads the same identities from encrypted repository secrets: `NEXUS_MAIN_KEYSTORE_B64`, `NEXUS_AGENT_KEYSTORE_B64`, `NEXUS_MAIN_STORE_PASSWORD`, `NEXUS_MAIN_KEY_PASSWORD`, `NEXUS_AGENT_STORE_PASSWORD`, and `NEXUS_AGENT_KEY_PASSWORD`. The workflow builds both the stable and agent-preview APKs and runs the same certificate checks before packaging.

Run `./gradlew :app:verifyNexusSigning` to check the local setup without building an APK. Never install an APK merely because it compiled; the signing check proves identity continuity, not that the build is newer than a device's installed app.
