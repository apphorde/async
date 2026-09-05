# Reader Vault

Reader Vault is a self-hosted, one-way Android backup service with a small web browser. It is deliberately narrower than Dropbox: Android uploads selected shared-storage folders, the server retains immutable file versions, and the browser lets the account owner browse, inspect history, and download files.

The v1 client supports `DCIM`, `Download`, `Pictures`, and `Movies` on Android 11+. It needs Android's **All files access** special permission. It cannot read other apps' private `/data/data` storage on an unrooted device.

## V1 status

Implemented:

- Email/password accounts with bcrypt password hashes and 30-day revocable sessions.
- Android login, device registration, SHA-256 file scanning, direct streaming upload, immutable versions, and server verification.
- Per-folder seven-day automatic deletion after successful remote verification.
- A persistent foreground sync service plus Wi-Fi-only WorkManager recovery every six hours.
- Browser sign-in, folder browsing, version history, and downloads.
- A self-contained Go server with FileBin, filesystem, and MinIO/S3 blob backends; atomic staging/commit; path validation; and an end-to-end test.

Deliberately not implemented in v1:

- Presigned multipart uploads. V1 streams staged content through the sync API; the server uses MinIO's S3 API at commit time.
- Client-side envelope encryption.
- Image/OCR/tag processing, previews, search, or virtual organization.
- Android trash-first deletion, custom roots, device revocation UI, and restore-to-device.

The client currently deletes a verified file directly when auto-delete is selected. Treat auto-delete as a backup policy, test it with a nonessential folder first, and ensure server backups exist. Direct deletion is necessary for unattended cleanup; Android's MediaStore trash API needs an interactive user confirmation and cannot provide the specified autonomous behavior reliably.

## Architecture

```text
Android client -- HTTPS + bearer token --> Reader Vault API --> immutable blobs
Browser ------ HTTPS + session cookie --> Reader Vault API --> metadata + blobs
```

The supplied Compose deployment stores opaque immutable-version payloads in a password-protected FileBin bin. The API protocol uses an explicit prepare, upload, commit, and verify sequence. Filesystem and MinIO/S3 backends remain available for local development, tests, and the future storage migration.

For production beyond a single API instance, use PostgreSQL for metadata and a small worker service. The worker should consume committed-version events and generate encrypted derived metadata for EXIF, thumbnails, OCR, tags, and future virtual folders. Original file versions must remain immutable; organization should be metadata views rather than physical rewrites.

## Encryption model

V1 requires HTTPS and private server/blob storage but does not yet encrypt client content before upload. This is intentional: the server needs plaintext to render browser downloads and later run classification jobs.

The planned extension is standard envelope encryption: Android encrypts each file with a random AEAD data key and wraps that small key with a server public key. The browser preview/download service and dedicated worker unwrap the key with the private key only for authorized operations. This protects blobs at rest but does not make the server zero-knowledge. Never implement direct RSA encryption of complete files.

## Run the server

Build and run locally:

```sh
cd server
go test ./...
go run .
```

Open `http://localhost:8080` and create the first account. Only the first account can register by default. Set `ALLOW_REGISTRATION=true` only when additional registrations are intentionally needed.

Passwords must be 12 to 72 UTF-8 bytes.

For container deployment:

```sh
cd server
docker compose up --build
```

Put a TLS reverse proxy in front of the service, set `SECURE_COOKIES=true`, persist the `/data` volume, and back it up. Do not expose the data directory or a future MinIO endpoint publicly. The Android client requires an `https://` endpoint.

For the supplied FileBin setup, copy `server/.env.example` to `server/.env`, create a private password-protected bin using the FileBin API, and set `FILEBIN_ID` and `FILEBIN_PASSWORD`. FileBin credentials belong only in this server-side `.env` file.

## Install Android

Build an APK:

```sh
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

In the app:

1. Grant All files access in the Android settings screen.
2. Enter the public HTTPS server URL and account credentials, then sign in.
3. Select the source folders. Enable auto-delete only for folders whose files should be removed seven days after a verified backup.
4. Start sync and allow notifications.
5. In OxygenOS, set battery use to Unrestricted, permit auto-launch/background activity where offered, and do not task-kill the app.

The foreground notification represents the active sync loop. The scheduled Wi-Fi worker recovers periodic reconciliation if the foreground process is killed.

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
