# Build & deploy runbook

Since the server moved off the dev laptop, "publish" is two separate jobs that used to be
one. `tools/publish_apk.ps1` writes into `gigi-server/downloads/` **on the machine it runs
on** — which is no longer the machine serving the site. Publishing without a copy step now
silently updates nothing.

Two independent pipelines:

| | Changes | Reaches users via |
|---|---|---|
| **App** | anything under `app/` | build APKs -> copy to server's `downloads/` -> `latest.json` |
| **Server** | `gigi-server/*.js`, `public/`, compose | copy to server -> restart container |

---

## 0. Before anything: commit

The working tree is the only copy of uncommitted work, and there is now more than one
checkout of this repo in play. Commit before building, moving folders, or letting another
tool touch the tree.

```
git checkout -b <branch>
git add -A
git commit -m "..."
```

---

## 1. Ship an app update

**On the dev machine:**

1. Bump `versionCode` **and** `versionName` in `app/build.gradle.kts`. versionCode must
   increase or the in-app updater will not offer it — it compares integers, not names.
2. Build:
   ```
   $env:TMP="C:\T"; $env:TEMP="C:\T"
   .\gradlew.bat :app:assembleRelease
   ```
   Use `:app:assembleRelease`, not `assembleRelease` — the unrelated `gigi-plugin` module
   fails on a missing godot AAR and takes the whole build down with it.
3. Publish locally, which produces the APKs and a correct `latest.json`:
   ```
   pwsh -File tools\publish_apk.ps1
   ```

**Then get `gigi-server/downloads/` onto the server.** This is the step that did not exist
before the move. Copy the whole directory: the five APKs, the five versioned copies, and
`latest.json`.

**Verify from anywhere:**
```
curl -s https://gigi.iamanraj.com/downloads/latest.json
curl -sI https://gigi.iamanraj.com/downloads/gigi-<ver>-arm64-v8a.apk
```
`versionCode` must be the new one and the ABI URL must return 200.

**Warm the edge** so the first real download is not served from the origin's uplink. A
25 MB object needs ~30s to finish caching, so fetch, wait, then confirm:
```
curl -s -o /dev/null -r 0-8388608 https://gigi.iamanraj.com/downloads/gigi-<ver>-arm64-v8a.apk
sleep 25
curl -sI -r 0-1024 https://gigi.iamanraj.com/downloads/gigi-<ver>-arm64-v8a.apk | grep -i cf-cache-status
```
Want `HIT`.

---

## 2. Ship a server change

`server.js`, `app_settings.js`, `plan_catalog.js` and `live_routes.js` are bind-mounted in
`docker-compose.yml`, so those need only a restart — no image rebuild:

```
node --check server.js          # never restart on a syntax error
docker compose restart gigi-server
curl -s localhost:2803/healthz
```

Anything else in the image (Dockerfile, `public/`, deps) needs:
```
docker compose up -d --build gigi-server
```

Adding or changing a **volume mount** needs `up -d`, not `restart` — restart reuses the old
container spec and the mount silently will not appear.

---

## 3. Rules learned the hard way

- **Never run two hosts on the same tunnel token.** Both cloudflared instances connect and
  Cloudflare bounces between them. Stop one before starting the other.
- **Never delete a published versioned APK.** They are served
  `Cache-Control: immutable, max-age=31536000`, so Cloudflare keeps serving them for a year
  after the origin file is gone — and any in-flight download hits a mix of cached 206s and
  origin 404s depending on the PoP.
- **Give compose commands a long timeout.** A tool timeout that kills `up --force-recreate`
  mid-rename leaves tangled container names and takes the site down.
- **Disable Docker Desktop autostart on the dev laptop.** Otherwise it silently resurrects
  the old stack on login and fights the real server for the tunnel.
- **`restart: unless-stopped` does not recover from an explicit stop.** If Docker is
  stopped, containers stay down until something starts them.

---

## 4. Rollback

`latest.json` is the only thing that decides what users are offered. To roll back, restore
the previous `latest.json` — the older versioned APKs are still on disk and still cached.
Do not delete the newer ones.
