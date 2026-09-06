# Reader Vault Android Client

Reader Vault is a minimal, one-way Android backup client for the Reader Vault server. It is deliberately narrower than Dropbox: Android uploads selected shared-storage folders, the server retains immutable file versions, and the browser lets the account owner browse, inspect history, and download files.

The current prototype supports `DCIM`, `Download`, `Pictures`, and `Movies` on Android 11+. It needs Android's **All files access** special permission. It cannot read other apps' private `/data/data` storage on an unrooted device.

## Current Status

Implemented:

- HTTPS server URL and email/password login.
- Android device registration and bearer-token authentication.
- Android Keystore-backed encrypted preferences for the server token and settings.
- All-files access settings flow and notification permission request.
- Selectable preset roots: `DCIM`, `Download`, `Pictures`, and `Movies`.
- Recursive file scanning with hidden-file and hidden-directory exclusion.
- SHA-256 hashing, streaming upload, remote commit, and remote verification.
- Immutable server-side versions when a file changes.
- Per-root seven-day automatic deletion after successful verification.
- One-shot foreground sync for manual runs.
- Wi-Fi-only WorkManager reconciliation every 15 minutes.
- Server-side browser login, folder browsing, version history, and downloads.

The server is maintained in the separate `async-server` repository. Its current interim storage backend is a password-protected FileBin bin; filesystem and MinIO/S3 backends are also available.

The Android client is a prototype and has not yet been validated on a real OxygenOS device in this workspace. The Android Gradle build was blocked here by a local Gradle daemon IPC failure, not by a reported Java compilation error.

## Build And Install

### Requirements

- Android SDK with API 34 platform and build tools.
- Java 17 or 21.
- Gradle wrapper access to `gradle-8.14.4`.
- A USB-debuggable Android 11+ device or emulator.
- `adb` on `PATH`.

Clone and build:

```sh
git clone git@github.com:apphorde/async.git
cd async
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Install it with:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

If the Android SDK is not in its standard location, create a local, uncommitted `local.properties` file containing the local SDK path, for example:

```properties
sdk.dir=/absolute/path/to/Android/Sdk
```

Run unit tests with:

```sh
./gradlew test
```

### Device Setup

1. Deploy the separate `async-server` repository at a public HTTPS URL.
2. Open the server web UI and register the first account. Passwords must be 12 to 72 UTF-8 bytes.
3. Launch the Android app and grant **All files access**.
4. Enter the server URL, email, and password, then sign in.
5. Select one small, nonessential folder for the first test.
6. Start a manual sync and wait for the completion notification.
7. Open the server web UI and verify the file, download, and version history.
8. Enable automatic deletion only after the upload and restore path has been tested.
9. On OxygenOS, set battery use to **Unrestricted**, allow auto-launch/background activity where available, and do not task-kill the app.

The Android app currently expects the server API to be available at the URL entered in its settings. The deployed test server is `https://sync.api.apphor.de`.

## Sync Safety

- A file is uploaded when its path is new or its size/modified-time metadata changes.
- Files are fully rehashed after metadata changes and at least once per day.
- A file is not considered backed up until the server reports successful remote verification.
- The seven-day cleanup timer starts at that verification time.
- Before cleanup, the client hashes the local file again and verifies the latest server version.
- If the bytes differ, deletion is skipped and a new version is uploaded.
- Auto-delete currently permanently deletes the local file. It does not use Android Trash.

## Deliberately Missing

The following features are not implemented yet:

- Real-device/OxygenOS validation and release signing.
- Upload resume, multipart transfer, and bandwidth throttling.
- MediaStore/file observer event ingestion; periodic scanning remains the source of truth.
- Custom folder selection through the Storage Access Framework.
- Additional user-configurable roots and exclusion rules.
- Android Trash-first cleanup and a recovery/grace-period UI.
- Remote device listing, device revocation, and token rotation UI.
- Logout/session renewal and a user-facing server connection test.
- A local Room database for file state; the prototype uses encrypted preferences.
- Conflict handling across multiple devices for the same path.
- Server restore-to-device support.
- Photo/video previews, thumbnails, search, EXIF extraction, OCR, tags, people, and invoice processing.
- Client-side envelope encryption.
- Presigned multipart uploads. The prototype streams through the Reader Vault API; the server stores committed bytes in FileBin.
- Server PostgreSQL metadata, rate limiting, password reset, audit logs, and multi-instance support.

The current deletion behavior is intentionally conservative about remote verification but irreversible locally. Treat auto-delete as a backup policy, test it with a nonessential folder first, and ensure the server/FileBin data is backed up.

## Architecture

```text
Android client -- HTTPS + bearer token --> Reader Vault API --> immutable blobs
Browser ------ HTTPS + session cookie --> Reader Vault API --> metadata + blobs
```

The supplied server deployment stores opaque immutable-version payloads in a password-protected FileBin bin. The API protocol uses an explicit prepare, upload, commit, and verify sequence. Filesystem and MinIO/S3 backends remain available for local development, tests, and the future storage migration.

For production beyond a single API instance, use PostgreSQL for metadata and a small worker service. The worker should consume committed-version events and generate encrypted derived metadata for EXIF, thumbnails, OCR, tags, and future virtual folders. Original file versions must remain immutable; organization should be metadata views rather than physical rewrites.

## Encryption model

V1 requires HTTPS and private server/blob storage but does not yet encrypt client content before upload. This is intentional: the server needs plaintext to render browser downloads and later run classification jobs.

The planned extension is standard envelope encryption: Android encrypts each file with a random AEAD data key and wraps that small key with a server public key. The browser preview/download service and dedicated worker unwrap the key with the private key only for authorized operations. This protects blobs at rest but does not make the server zero-knowledge. Never implement direct RSA encryption of complete files.

## Server Repository

The backend, Docker image, FileBin configuration, browser UI, and HTTP protocol are in:

```text
git@github.com:apphorde/async-server.git
```

See that repository's README for local Go tests, Docker image builds, FileBin environment variables, and production deployment. Android background scheduling cannot guarantee exact 15-minute execution; Android and OxygenOS may defer WorkManager jobs.

## Upload protocol

All API calls use `Authorization: Bearer <login token>` after login.

1. `POST /api/login` authenticates the Android client.
2. `POST /api/devices` creates a device record.
3. `POST /api/uploads/prepare` receives device ID, relative POSIX path, byte size, and lowercase SHA-256.
4. `PUT upload_url` streams the exact file bytes to staging.
5. `POST commit_url` atomically makes a new immutable version.
6. `GET /api/verify?path=...` rehashes the latest committed blob. Only then does the client record its seven-day deletion clock.

Every edited file gets a new remote version. Files unchanged by SHA-256 are not re-uploaded. The client re-verifies immediately before deleting an eligible local file.

## Operational boundaries

- Maximum default upload size is 2 GiB; configure `MAX_UPLOAD_BYTES` on the server for larger videos.
- The v1 local JSON metadata store is single-node only. Use PostgreSQL before running multiple API replicas.
- Login tokens and folder state use Android Keystore-backed encrypted preferences.
- The server needs rate limits, password-reset delivery, backup monitoring, and audit/event persistence before internet-scale use.
- Do not enable external AI processing without explicit user consent because it sends decrypted content to that provider.

See `server/README.md` for endpoint and storage-backend details.
