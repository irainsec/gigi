# Gigi — Play Store Launch Checklist

Status as of 2026-06-12. All code-side blockers are done; the items below are
manual steps in external consoles, plus the data-safety declarations.

## 1. Secrets to rotate (committed to git history)

Both of these are in old commits even though the files are now gitignored.
Rotate first, purge history second (purging without rotating accomplishes nothing).

- [ ] **Firebase service account key** (`gigi-server/serviceAccountKey.json`)
  - Firebase Console → Project settings → Service accounts → Generate new private key
  - Google Cloud Console → IAM → Service accounts → delete the old key
  - Replace the file on disk (Docker build copies it) or set `FIREBASE_SERVICE_ACCOUNT` env (JSON) and remove the COPY from the Dockerfile
- [ ] **Cloudflare tunnel token** (was in the tracked `.env.example`)
  - Cloudflare Zero Trust → Tunnels → rotate/recreate the tunnel token → update `gigi-server/.env`
- [ ] Purge both from git history (e.g. `git filter-repo --invert-paths --path gigi-server/serviceAccountKey.json` and a replace for the token in `.env.example`), then force-push if a remote exists
- [x] `.env` rotated locally (admin / mongo-express / MinIO passwords) — values in `gigi-server/.env`

## 2. Play Console setup

- [ ] Create app (package `com.aman.gigi`), upload the signed release AAB (`bundleRelease`)
- [ ] **Subscriptions** → create products:
  - `gigi_plus_monthly` — ₹99/month
  - `gigi_pro_monthly` — ₹199/month
  - (UpgradeSheet shows these prices; keep them in sync)
- [ ] **API access** → link the Google Cloud project → grant the Firebase service account
  the "View financial data" + order-management permissions so the server can verify purchases.
  Until this is done, `/api/billing/verify-purchase` returns 402 for real purchases.
- [ ] Set `PLAY_PACKAGE_NAME=com.aman.gigi` in `gigi-server/.env` (defaults correctly, but be explicit)
- [ ] Privacy policy URL: `https://gigi.iamanraj.com/privacy-policy.html`
- [ ] Account deletion URL: `https://gigi.iamanraj.com/delete-account.html`

## 3. Phone OTP — RESOLVED (2026-06-12)

- [x] `OTP_DEV_MODE=false` set and deployed — OTPs are no longer echoed in API responses
- [x] The app is Google Sign-In only (no phone-login UI exists), so nothing user-facing broke
- [x] The web deletion page no longer depends on OTP: it files a deletion request
  (phone or email) that you process within 7 days from the admin API
  (`GET /admin/data/deletion-requests`, then delete the matched member and
  `PUT .../deletion-requests/:id {"status":"processed"}`)
- [ ] Optional, post-launch: integrate an SMS provider in `issueOtpChallenge()` if you
  ever want phone login back

## 4. Data-safety form answers

| Data type | Collected? | Shared? | Purpose | Optional? |
|---|---|---|---|---|
| Phone number | Yes | No | Account management | No (if phone login kept) |
| Email + name (Google Sign-In) | Yes | No | Account management | No |
| Photos (in-app camera + picker) | Yes | With connected partner only | App functionality | Yes |
| Approximate/precise location | Yes | With connected partner only | App functionality (distance badge, presence) | Yes |
| User-generated content (drawings, notes, cards) | Yes | With connected partner only | App functionality | No |
| Device IDs | Yes | No | App functionality, account management | No |
| Purchase history (subscription status) | Yes | No | App functionality | Yes |

- Data is encrypted in transit (wss/https via Cloudflare). Deletion path exists (in-app + web).
- "Shared" above means user-to-user sharing within the app, not sharing with third parties — declare NO third-party sharing.

## 5. Release build

- [ ] `gradlew bundleRelease` with the release signing env vars set
  (`GIGI_RELEASE_STORE_FILE/PASSWORD/KEY_ALIAS/KEY_PASSWORD`; keystore `my-release-key.jks`)
- [ ] Confirm `GIGI_SERVER_URL` resolves to `wss://gigi.iamanraj.com` (default)
- [ ] Crashlytics: first crash reports appear after the app runs with google-services.json from
  the Firebase project (already bundled); enable Crashlytics in Firebase Console if prompted
- [ ] Test on a real device: Google Sign-In → create connection → hit free limits → UpgradeSheet
  → (sandbox) purchase → tier upgrades

## 6. Server before going live

- [x] All HTTP endpoints authenticated; rate limiting active
- [x] Mongo / mongo-express / MinIO bound to localhost only
- [x] Tier limits enforced server-side; expired plans auto-downgrade
- [x] `OTP_DEV_MODE=false` deployed (see §3)
- [ ] Check `GET /admin/data/deletion-requests` periodically (or before each release)
  and process pending requests within 7 days
- [ ] After rotating the Firebase key, rebuild the image: `docker compose up --build -d gigi-server`
