# Customs RMS — Deployment Guide

Practical, app-specific guide for deploying the Customs Records Management System to a
production server. For the pre-release verification steps, see
[RELEASE_CHECKLIST.md](RELEASE_CHECKLIST.md) — run that **after** following this guide.

> **Read this first — the 4 things that break production if you miss them**
> 1. `APP_CORS_ALLOWED_ORIGINS` must list your real frontend URL. It controls **both** the
>    REST API **and** the WebSocket handshake. If it's wrong, login and live notifications
>    silently fail.
> 2. The frontend is compiled with `VITE_API_BASE_URL` **baked in at build time**. You must
>    set it before `npm run build`, not at runtime.
> 3. Your reverse proxy must forward the WebSocket **Upgrade** headers for `/ws/` or
>    real-time notifications won't connect.
> 4. Run the backend with `SPRING_PROFILES_ACTIVE=prod`. This switches the schema to
>    `validate` (no auto-DDL) and disables user seeding.

---

## 1. Architecture: what you are deploying

| Component | Tech | Build output | Runtime |
|-----------|------|--------------|---------|
| Backend | Spring Boot 4.0.2 (Java 17) | `rms-backend-0.0.1-SNAPSHOT.jar` | Java process on port `8080` |
| Frontend | Vue 3 + Vite | static files in `dist/` | served by Nginx (or any static host) |
| Database | MySQL 8.0 | — | port `3306` |
| Realtime | WebSocket (`/ws/notifications`) | (part of backend) | same port as backend |
| File storage | Local filesystem | — | `APP_UPLOAD_DIR` (must persist) |

Recommended topology: **Nginx** terminates TLS, serves the SPA, and reverse-proxies
`/api` and `/ws` to the backend. MySQL and the backend stay on a private network.

```
Browser ──HTTPS──> Nginx ──/ ────────> static dist/ (SPA)
                     │ ──/api ────────> backend :8080
                     │ ──/ws  (wss) ──> backend :8080  (WebSocket upgrade)
                                          └──> MySQL :3306
                                          └──> APP_UPLOAD_DIR (disk)
```

---

## 2. Prerequisites

- Linux server (this guide assumes Ubuntu/Debian; adapt paths for others)
- **JDK 17** (Temurin recommended — matches the build)
- **MySQL 8.0**
- **Node 20+** (only to *build* the frontend; not needed at runtime)
- **Nginx**
- A TLS certificate (Let's Encrypt / `certbot`)

---

## 3. Environment variables (the production config contract)

The backend reads all secrets and environment-specific values from environment variables.
Spring Boot relaxed binding means `SPRING_DATASOURCE_URL` overrides `spring.datasource.url`,
etc., so you can point at any database host without editing files.

### Required

| Variable | Example | Notes |
|----------|---------|-------|
| `SPRING_PROFILES_ACTIVE` | `prod` | **Must be `prod`.** Enables schema `validate`, disables seeding. |
| `SPRING_DATASOURCE_URL` | `jdbc:mysql://db-host:3306/customs_rms?useSSL=true&serverTimezone=UTC` | The base config hardcodes `localhost`; override it here for a remote DB. |
| `DB_USERNAME` | `rms_app` | Dedicated DB user (not root). |
| `DB_PASSWORD` | `<strong-password>` | |
| `JWT_SECRET` | `<base64, ≥256-bit>` | **Generate a fresh one** (see §5). Rotating it logs everyone out. |
| `APP_UPLOAD_DIR` | `/var/lib/customs-rms/uploads` | Must exist, be writable, and **persist across deploys** (attachments live here). |
| `APP_CORS_ALLOWED_ORIGINS` | `https://rms.customs.gov.lk` | Comma-separated. Controls REST **and** WebSocket origins. No trailing slash. |

### Optional (sensible defaults exist)

| Variable | Default | Notes |
|----------|---------|-------|
| `SERVER_PORT` | `8080` | Backend listen port. |
| `JWT_EXPIRATION_MS` | `28800000` (8 h) | Session token lifetime. |
| `DC_AUTO_FORWARD_POLL_MS` | `60000` | DC auto-forward scheduler interval. |
| `SPRING_DATASOURCE_USERNAME` / `_PASSWORD` | falls back to `DB_USERNAME`/`DB_PASSWORD` | Only if you prefer the Spring-native names. |

> ⚠️ **Do not** set `APP_SEED_ENABLED=true` in production. The `prod` profile forces it off;
> seeding is only for local/dev/e2e. See §7 for the first-admin bootstrap.

Keep these in a root-only file such as `/etc/customs-rms/backend.env` (chmod 600), referenced
by the systemd unit in §6 — never commit them.

---

## 4. Database setup

```sql
CREATE DATABASE customs_rms CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'rms_app'@'%' IDENTIFIED BY '<strong-password>';
GRANT SELECT, INSERT, UPDATE, DELETE ON customs_rms.* TO 'rms_app'@'%';
-- The first boot needs DDL to create the schema (see note below):
GRANT CREATE, ALTER, INDEX, REFERENCES ON customs_rms.* TO 'rms_app'@'%';
FLUSH PRIVILEGES;
```

**Schema creation note.** The `prod` profile runs Hibernate with `ddl-auto=validate`, which
**does not create tables** — it only verifies they exist. Two options:

- **Recommended for first deploy:** do a one-time bootstrap run with
  `SPRING_PROFILES_ACTIVE=prod` but temporarily override
  `SPRING_JPA_HIBERNATE_DDL_AUTO=update` so Hibernate creates the schema, then remove that
  override for all subsequent runs. The app also runs a small built-in migration
  (`DocumentDateColumnMigration`) on startup that adds a few columns idempotently.
- **Stricter shops:** generate the DDL from a staging DB and apply it via your migration tool,
  then keep `validate`.

**Schema migrations between releases.** Because `prod` uses `validate`, any new column a release
introduces must be added **before** the new code starts, or startup fails schema validation. For the
optimistic-locking release, add the `version` column to an existing `documents` table first:

```sql
ALTER TABLE documents ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
```

(A fresh database created via the bootstrap step above already includes it.)

After the schema is stable you can drop `CREATE, ALTER, INDEX, REFERENCES` from the app DB
user so the running app has data-only privileges.

**Always take a backup before each release:**
```bash
mysqldump --single-transaction -u root -p customs_rms > backup-$(date +%F).sql
```

---

## 5. Build

### Generate a JWT secret (once)
```bash
openssl rand -base64 48     # paste the output as JWT_SECRET
```

### Backend
```bash
cd rms-backend
./mvnw -q clean package        # runs tests; use -DskipTests only if CI already gated them
# → target/rms-backend-0.0.1-SNAPSHOT.jar
```

### Frontend — set the API URL BEFORE building
```bash
cd rms-frontend
npm ci
VITE_API_BASE_URL=https://rms.customs.gov.lk npm run build
# → dist/   (static files; VITE_API_BASE_URL is now compiled in)
```

`VITE_API_BASE_URL` is the **base** origin; the app appends `/api` and derives the
`wss://…/ws` URL from it automatically. Point it at the public host that Nginx serves, not at
`:8080` directly.

---

## 6. Run the backend (systemd)

Copy the jar to the server (e.g. `/opt/customs-rms/app.jar`) and create
`/etc/systemd/system/customs-rms.service`:

```ini
[Unit]
Description=Customs RMS backend
After=network.target mysql.service

[Service]
User=customs-rms
EnvironmentFile=/etc/customs-rms/backend.env
ExecStart=/usr/bin/java -jar /opt/customs-rms/app.jar
SuccessExitStatus=143
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now customs-rms
sudo systemctl status customs-rms
# Verify it is up:
curl -fsS http://localhost:8080/api/health      # → "Backend is running ✅"
```

---

## 7. First admin (production has no seeded users)

Because seeding is disabled in `prod`, a fresh database has **no users**. Bootstrap one admin,
then create the rest in the UI:

- **Easiest:** for the very first boot only, add these to the env file, start once, then
  **remove them and restart**:
  ```
  APP_SEED_ENABLED=true
  APP_SEED_ADMIN_PASSWORD=<temp-strong-password>
  APP_SEED_DEFAULT_PASSWORD=<temp-strong-password>
  ```
  > Note: seeding is wired to the local/dev/e2e profiles. If your build does not seed under
  > `prod`, use the SQL method below instead.
- **Explicit SQL:** insert one admin with a BCrypt hash you generate
  (`htpasswd -bnBC 10 "" 'YourPassword' | tr -d ':\n'` gives a `$2y$…` hash), assigning the
  `ADMIN` role id from the `roles` table.

Immediately log in and change the password. Then create real workflow users (DC/DDC/SC/PMA…)
from **Users**, and review **Permissions**.

---

## 8. Nginx — TLS, SPA, and the WebSocket proxy

```nginx
# Rate-limit zone for the login endpoint. This directive lives in the http{} context, so put it
# at the top of your conf.d/*.conf snippet (which is included inside http{}), NOT inside server{}.
# It throttles brute-force password guessing before requests reach the app. 10 requests/minute/IP
# is generous for real staff (who log in once or twice a day) but stops rapid guessing. If all
# staff share one office egress IP, keep the rate per-minute (as here) so normal logins are unaffected.
limit_req_zone $binary_remote_addr zone=login:10m rate=10r/m;

server {
    listen 443 ssl http2;
    server_name rms.customs.gov.lk;

    ssl_certificate     /etc/letsencrypt/live/rms.customs.gov.lk/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/rms.customs.gov.lk/privkey.pem;

    # Must be ≥ the backend's 25 MB attachment limit, plus headroom.
    client_max_body_size 30m;

    # Security headers (apply to every response via inheritance — the location blocks below define
    # no add_header of their own, so these are inherited). `always` sends them on error responses too.
    add_header X-Content-Type-Options "nosniff" always;
    add_header Referrer-Policy "no-referrer" always;
    add_header Permissions-Policy "geolocation=(), microphone=(), camera=()" always;
    # Content-Security-Policy — the XSS safety net. Tuned to what the SPA actually loads:
    #   script-src 'self'            → Vite-bundled JS served from this origin only
    #   style-src  ... 'unsafe-inline' + fonts.googleapis.com → Vue inline styles + the Manrope @import
    #   font-src   ... fonts.gstatic.com → Google-hosted font files
    #   img-src    'self' data: blob: → logos, plus profile-picture/attachment previews
    #   connect-src 'self'           → REST + the wss:// WebSocket (same origin as this page)
    #   frame-ancestors 'self'       → clickjacking protection for the page
    # ROLL OUT SAFELY: deploy first as "Content-Security-Policy-Report-Only" (below), load the app,
    # confirm the browser console shows no violations (login fonts, live notifications, image
    # previews), THEN rename the header to "Content-Security-Policy" to enforce.
    add_header Content-Security-Policy "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; font-src 'self' https://fonts.gstatic.com; img-src 'self' data: blob:; connect-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'self'; form-action 'self'" always;

    # 1) SPA static files
    root /var/www/customs-rms;          # contents of rms-frontend/dist
    index index.html;
    location / {
        try_files $uri $uri/ /index.html;   # client-side routing fallback
    }

    # 2a) Login endpoint — rate-limited to slow brute-force. An exact-match (=) location wins over
    #     the /api/ prefix below, so only login is throttled. burst=5 lets a real user mistype a few
    #     times without being delayed, then the 10r/m rate kicks in.
    location = /api/auth/login {
        limit_req zone=login burst=5 nodelay;
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # 2) REST API
    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # 3) WebSocket — REQUIRED for real-time notifications
    location /ws/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;       # ← the line people forget
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 3600s;                     # keep idle sockets alive
    }
}

server {                                  # HTTP → HTTPS redirect
    listen 80;
    server_name rms.customs.gov.lk;
    return 301 https://$host$request_uri;
}
```

```bash
sudo cp -r rms-frontend/dist/* /var/www/customs-rms/
sudo nginx -t && sudo systemctl reload nginx
```

Because the browser origin is now `https://rms.customs.gov.lk`, that exact value **must** be in
`APP_CORS_ALLOWED_ORIGINS`.

**Tuning the CSP for your topology — two cases that need an edit:**

- **API/WebSocket on a different origin.** The policy above assumes Nginx serves the SPA, `/api`,
  and `/ws` from one origin (so `connect-src 'self'` covers them). If you split them (e.g. SPA at
  `rms.customs.gov.lk`, API at `api.customs.gov.lk`), add both to `connect-src`, including the
  `wss://` scheme for the socket:
  `connect-src 'self' https://api.customs.gov.lk wss://api.customs.gov.lk`.
- **App fails to load with `script-src 'self'`.** A Vite build can emit a tiny inline module-preload
  script. If the console reports it blocked, the clean fix is to disable that inline polyfill rather
  than weaken the policy with `'unsafe-inline'` — set in `vite.config.js`:
  `build: { modulePreload: { polyfill: false } }`, then rebuild.

> Optional hardening: the login page pulls the Manrope font from Google
> ([LoginPage.vue](rms-frontend/src/pages/LoginPage.vue)). Self-hosting it removes the only
> external request (better privacy — no client IP leak to Google — and lets you drop
> `fonts.googleapis.com`/`fonts.gstatic.com` from the CSP). Not required, but recommended for a
> government deployment.

---

## 9. Security hardening checklist

- [ ] Fresh, unique `JWT_SECRET` (never the dev value) — ≥256-bit, base64.
- [ ] Dedicated MySQL user with least privilege (drop DDL grants once schema is stable).
- [ ] `APP_CORS_ALLOWED_ORIGINS` lists **only** your real frontend origin(s).
- [ ] TLS enforced; HTTP redirects to HTTPS; WebSocket uses `wss://`.
- [ ] `APP_UPLOAD_DIR` is outside the web root and not served directly by Nginx.
- [ ] `backend.env` is `chmod 600`, owned by the service user; secrets never committed.
- [ ] `app.seed.enabled` is off (guaranteed by `prod`); temp seed creds removed after bootstrap.
- [ ] OS firewall exposes only 80/443; `3306` and `8080` stay on the private network.
- [ ] Security response headers are set at Nginx (§8): `Content-Security-Policy` (enforcing, not
      Report-Only, once verified), `X-Content-Type-Options`, `Referrer-Policy`, `Permissions-Policy`.
- [ ] Login brute-force is throttled at **two layers**: Nginx (`limit_req` on `/api/auth/login`, §8)
      and the app itself (per-IP failure throttle → HTTP 429, env `LOGIN_MAX_FAILED_ATTEMPTS` /
      `LOGIN_BLOCK_MINUTES`). Both key on IP, not username, to avoid letting an attacker lock out a
      legitimate user. If your policy requires true per-account lockout, add it deliberately on top.

---

## 10. Health, logs, monitoring

- **Liveness:** `GET /api/health` → `Backend is running ✅`
- **Actuator:** `GET /actuator/health` (Spring Boot Actuator is on the classpath)
- **Logs:** `journalctl -u customs-rms -f`
- Alert if `/api/health` is non-200 for >1 min, or on repeated `Unhandled exception` lines.

---

## 11. Upgrades & rollback

**Deploy a new version**
1. `mysqldump` backup (§4).
2. Build backend jar and frontend `dist` (§5) with the same prod env values.
3. Replace `/opt/customs-rms/app.jar`; `sudo systemctl restart customs-rms`.
4. Sync `dist/` to `/var/www/customs-rms`; `sudo systemctl reload nginx`.
5. Wait for `GET /api/health` 200, then run [RELEASE_CHECKLIST.md](RELEASE_CHECKLIST.md).

**Rollback**
- Backend: restore the previous `app.jar` and restart.
- Frontend: restore the previous `dist/` and reload Nginx.
- Database: restore the pre-release `mysqldump` **only if** the release changed the schema and
  the new code can't run against the old data. Keep the previous jar + dist on disk so rollback
  is a copy, not a rebuild.

---

## 12. Troubleshooting (symptoms seen with this app)

| Symptom | Likely cause | Fix |
|---------|--------------|-----|
| Login does nothing / network error in console | Browser origin not in `APP_CORS_ALLOWED_ORIGINS` | Add the exact `https://…` origin, restart backend |
| Login works but no live notifications | Nginx not forwarding WebSocket upgrade, or WS origin blocked | Add the `/ws/` block (§8); ensure origin is in `APP_CORS_ALLOWED_ORIGINS` |
| Frontend calls `localhost:8080` in production | Built without `VITE_API_BASE_URL` | Rebuild frontend with the var set (§5) |
| Backend won't start: "Schema-validation: missing table" | `validate` profile against an empty DB | One-time bootstrap with `ddl-auto=update` (§4) |
| Backend won't start: datasource / access denied | Wrong `SPRING_DATASOURCE_URL` / DB creds | Verify URL host and the app DB user grants |
| Attachment upload fails ~25 MB | Nginx `client_max_body_size` too low | Set it ≥ 30m (§8); backend caps files at 25 MB |
| Everyone logged out after deploy | `JWT_SECRET` changed | Keep the secret stable across deploys |
| Uploaded files vanish after redeploy | `APP_UPLOAD_DIR` on ephemeral storage | Point it at a persistent volume |
| Blank page / missing fonts, images, or live notifications after enabling CSP | `Content-Security-Policy` too strict for this app's resources | Read the blocked directive in the browser console; roll out as `-Report-Only` first, then adjust per §8 (add a separate API origin to `connect-src`, or disable Vite's inline modulepreload) |
| Login returns 429 "Too many failed login attempts" | App-layer throttle tripped after repeated failures from one IP | Expected for brute-force; wait `LOGIN_BLOCK_MINUTES`, or raise `LOGIN_MAX_FAILED_ATTEMPTS` if staff share one egress IP |
```
