# Owner-only signed APK workflow

The workflow at `.github/workflows/signed-release.yml` builds `assembleRelease` and refuses to continue unless the owner-supplied keystore and every signing secret are present.

It never falls back to Android's debug keystore or to a generated keystore.

## 1. Keep the keystore private

Do **not** commit a `.jks` or `.keystore` file, even if it is temporarily placed in the project root. Both extensions are excluded by `.gitignore`.

Convert the keystore to a one-line Base64 value and save that value as a GitHub Actions secret.

### Linux

```bash
base64 -w 0 wordbattle-release.jks
```

### macOS

```bash
base64 < wordbattle-release.jks | tr -d '\n'
```

### Windows PowerShell

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("wordbattle-release.jks"))
```

## 2. Add repository secrets

Open **GitHub repository → Settings → Secrets and variables → Actions → New repository secret** and add:

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | The one-line Base64 keystore value |
| `STORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Alias contained in that keystore |
| `KEY_PASSWORD` | Password for that alias/private key |
| `GOOGLE_WEB_CLIENT_ID` | Optional for compilation, but required for working Google login |

Never put these values in Gradle files, workflow YAML, `local.properties`, commits, issues, or chat.

## 3. Build the APK

1. Open the repository's **Actions** tab.
2. Select **Build signed release APK**.
3. Choose **Run workflow**.
4. Download `WordBattle-signed-<run number>` from the run's Artifacts section.

A pushed tag such as `v1.0.0` also builds the APK and attaches it, its SHA-256 checksum, and signing-certificate report to the corresponding GitHub Release.

## Signing guarantees

Before building, the workflow:

1. restores only `KEYSTORE_BASE64` to `wordbattle-release.jks` in the runner's project root;
2. verifies the store password and alias with `keytool`;
3. passes only that path and the encrypted GitHub secrets to Gradle;
4. runs `assembleRelease`—never `assembleDebug`;
5. verifies the resulting APK with Android `apksigner`;
6. uploads the certificate report and checksum alongside the APK; and
7. deletes the temporary keystore even if the build fails.

`app/build.gradle.kts` also has a release-task guard. Missing keystore details cause the build to fail rather than produce an APK with another signing identity.
