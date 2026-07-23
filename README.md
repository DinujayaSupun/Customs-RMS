# Customs RMS

Document Records Management System for Sri Lanka Customs.

![Java](https://img.shields.io/badge/Java-17-blue?logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.2-brightgreen?logo=springboot)
![Vue](https://img.shields.io/badge/Vue-3-41b883?logo=vuedotjs)
![Vite](https://img.shields.io/badge/Vite-7-646cff?logo=vite)
![MySQL](https://img.shields.io/badge/MySQL-8-orange?logo=mysql)

Customs RMS is an internal workflow system for receiving, routing, reviewing, and completing
customs documents. Officers forward documents to one another, add minutes/remarks, attach files,
make approval decisions, and track every action through an audit trail — with role-based access
control and real-time notifications.

> **Just want to run it?** You don't need the local setup below (Java, Node, MySQL, `.env`).
> With Docker installed, `docker compose up --build` starts the whole stack — see
> **[QUICKSTART-DOCKER.md](QUICKSTART-DOCKER.md)**. The steps in this README are for **local
> development** (editing code with hot reload); production deployment is in
> **[DEPLOYMENT.md](DEPLOYMENT.md)**.

## Contents

- [Key features](#key-features)
- [Tech stack](#tech-stack)
- [Roles and the permission model](#roles-and-the-permission-model)
- [Project structure](#project-structure)
- [Architecture](#architecture)
- [Backend API areas](#backend-api-areas)
- [Prerequisites](#prerequisites)
- [Local development](#local-development)
- [Run with Docker](#run-with-docker)
- [Environment variables](#environment-variables)
- [Seeded roles and default users](#seeded-roles-and-default-users)
- [Authentication and file downloads](#authentication-and-file-downloads)
- [Real-time notifications](#real-time-notifications)
- [Testing](#testing)
- [Continuous integration](#continuous-integration)
- [Deployment](#deployment)
- [Further documentation](#further-documentation)

## Key features

- **JWT authentication** with Spring Security, plus short-lived scoped tokens for browser downloads.
- **Role and permission matrix** — granular, admin-configurable permissions per role.
- **Document lifecycle** — create, list, filter, view, edit, delete, and PUBLIC/PRIVATE visibility.
- **Workflow actions** — forward, return, approve, reject, mark done (issue), and reopen.
- **Forward to multiple recipients** — a primary "Report At" owner plus **CC** and **BCC** recipients,
  each with their own view/attachment/minute permissions.
- **Recipient groups** — WhatsApp-style group forwarding: forward a document to a group and any
  group admin can act on it, while other members stay copied in (CC-only, no action rights).
- **Minutes / remarks** timeline per document.
- **Attachments** — upload, download, versioning, and soft-delete, stored outside the web root.
- **Undo Send** — a short window to recall a forward/return before the recipient opens it.
- **Inbox and Sent** views, plus per-user **workload stats** (assigned / opened / unopened).
- **Real-time notifications** over WebSocket (document forwarded/returned, live permission updates).
- **Audit log** of every action, with filtering and CSV export.
- **Admin user management** — create, edit, activate, deactivate, reset password, merge, bulk
  deactivate/delete, and CSV export.
- **DC auto-forward scheduler** — reassigns timed-out, unopened documents to a configured officer.
- **Profile management** — personal details, password change, and profile picture.

## Tech stack

| Layer | Technology |
|-------|------------|
| Backend | Spring Boot 4.0.2, Java 17, Spring Security, Spring Data JPA (Hibernate) |
| Real-time | Spring WebSocket (`/ws/notifications`) |
| Frontend | Vue 3 (`<script setup>`), Vite, Vue Router, axios, lucide icons, Tailwind CSS + PostCSS |
| Database | MySQL 8 (production) · H2 in MySQL-compatibility mode (tests) |
| Auth | JWT access tokens + short-lived scoped download tokens (BCrypt password hashing) |
| Build / test | Maven Wrapper, npm, JUnit 5, Vitest, Playwright |

All backend database queries are written in portable JPQL, so the schema runs on any
Hibernate-supported database (verified on both MySQL and H2).

## Roles and the permission model

Seven roles model the Customs hierarchy:

| Role | Meaning |
|------|---------|
| `ADMIN` | System administrator (user and permission management) |
| `DC` | Director of Customs |
| `DDC` | Deputy Director of Customs |
| `SDDC` | Senior Deputy Director of Customs |
| `SC` | Superintendent of Customs |
| `ASC` | Assistant Superintendent of Customs |
| `PMA` | Personal Management Assistant |

Access is **permission-based**, not hard-coded to roles. An admin toggles a matrix of permissions
(for example `CREATE_DOCUMENT`, `FORWARD_DOCUMENT`, `APPROVE_DOCUMENT`, `VIEW_LOGS`, plus CC/BCC
view and attachment permissions) per role from the Permissions page. Changes take effect live —
affected users receive a WebSocket update and the UI re-evaluates their capabilities without a
re-login. The backend enforces every permission server-side regardless of what the UI shows.

## Project structure

```text
Customs-RMS/
|-- .github/workflows/         # CI pipeline
|-- package.json               # Root dev orchestration scripts
|-- scripts/                   # Local development helper scripts
|-- rms-backend/               # Spring Boot REST API + WebSocket (incl. Dockerfile)
|-- rms-frontend/              # Vue 3 SPA (incl. Dockerfile + nginx.conf)
|-- docker-compose.yml         # Local Docker stack (db + backend + frontend)
|-- docker-compose.prod.yml    # Production-style Docker stack (prod profile, secrets, bind mounts)
|-- packaging/                 # Offline image bundle (compose + run scripts + recipient README)
|-- make-bundles.ps1           # Builds shareable source / offline zips into bundles/
|-- QUICKSTART-DOCKER.md       # One-command Docker quickstart
|-- DEPLOYMENT.md              # Production deployment guide (Appendix A covers Docker)
|-- RELEASE_CHECKLIST.md       # Pre-release verification checklist
`-- README.md
```

## Architecture

Most user actions follow the same path through the system:

```text
Vue page -> frontend API wrapper -> Spring controller -> service -> repository -> database table
```

Example — the document list:

```text
rms-frontend/src/pages/DocumentsPage.vue
  -> rms-frontend/src/api/documents.api.js
  -> rms-backend/.../controller/DocumentController.java
  -> rms-backend/.../service/impl/DocumentServiceImpl.java
  -> rms-backend/.../repository/DocumentRepository.java
  -> documents table
```

The same pattern applies across modules:

| Area | Frontend entry | API wrapper | Controller | Service | Repository / table |
|------|----------------|-------------|------------|---------|--------------------|
| Login / profile | `LoginPage.vue`, `ProfilePage.vue` | `auth.api.js` | `AuthController` | Spring Security, `FileStorageService` | `UserRepository`, `users` |
| Documents | `DocumentsPage.vue`, `DocumentDetailsPage.vue` | `documents.api.js` | `DocumentController` | `DocumentServiceImpl` | `DocumentRepository`, `documents` |
| Inbox / workflow | `InboxPage.vue` | `documents.api.js` | `DocumentController` | `DocumentServiceImpl`, `DocumentRecipientServiceImpl` | `documents`, `document_movements`, `document_recipient_sets` |
| Minutes / remarks | `DocumentDetailsPage.vue`, `InboxPage.vue` | `documents.api.js` | `DocumentRemarkController` | — | `DocumentRemarkRepository`, `document_remarks` |
| Movements / history | `DocumentDetailsPage.vue` | `documents.api.js` | `DocumentMovementController` | — | `DocumentMovementRepository`, `document_movements` |
| Attachments | `DocumentDetailsPage.vue`, `InboxPage.vue` | `documents.api.js` | `DocumentAttachmentController` | `AttachmentServiceImpl`, `FileStorageService` | `DocumentAttachmentRepository`, upload folder |
| Audit logs | `LogsPage.vue` | `logs.api.js` | `LogsController` | `AuditLogServiceImpl` | `AuditLogRepository`, `audit_logs` |
| Admin users | `UsersPage.vue` | `auth.api.js` | `AdminUserController` | `AdminUserServiceImpl` | `UserRepository`, `RoleRepository` |
| Permissions | `PermissionsPage.vue` | `permissions.api.js` | `AdminPermissionController` | `PermissionServiceImpl`, `DcAutoForwardConfigServiceImpl` | `RolePermissionRepository`, `dc_auto_forward_config` |
| Notifications | `App.vue`, `services/realtimeNotifications.js` | WebSocket | `NotificationWebSocketHandler` | `RealtimeNotificationService` | in-memory session registry |
| Recipient groups | `GroupsPage.vue` | `auth.api.js` | `RecipientGroupController` | `RecipientGroupServiceImpl` | `RecipientGroupRepository`, `recipient_groups` |

Layer responsibilities:

- **Pages** own screen state, forms, and user interactions.
- **API wrappers** own HTTP paths and request/response calls.
- **Controllers** are thin HTTP adapters: routing, auth lookup, and request validation.
- **Services** own business rules, permissions, workflow transitions, audit logging, and files.
- **Repositories** own database reads/writes (portable JPQL).
- **DTOs** define the API shape sent to the frontend (entities are never exposed directly).

When adding a feature, follow the flow above: a new document action usually needs a Vue control,
an API wrapper function, a controller route, service permission/business logic, repository access,
audit logging, and focused tests.

## Backend API areas

| Path | Purpose |
|------|---------|
| `/api/health` | Liveness check |
| `/api/auth` | Login, current user, profile, password, profile picture |
| `/api/documents` | List/create/view/edit/delete documents (legacy `/api/reports` still redirects) |
| `/api/documents/my-inbox`, `/sent-messages` | Inbox and sent views |
| `/api/documents/my-workload-stats` | Per-user assigned/opened/unopened counts |
| `/api/documents/{id}/forward` · `/return` · `/approve` · `/reject` · `/issue` · `/reopen` · `/undo-send` | Workflow actions |
| `/api/documents/{id}/recipients` · `/remarks` · `/movements` · `/attachments` | Recipients, minutes, history, files |
| `/api/attachments/{id}` | Download / delete an attachment |
| `/api/groups` | Recipient group CRUD, membership, and held-documents view |
| `/api/audit-logs` | Audit log search, filter options, CSV export |
| `/api/admin/users` | Admin user management (incl. merge, bulk ops, CSV export) |
| `/api/admin/permissions` | Permission matrix and DC auto-forward config |
| `/ws/notifications` | Authenticated WebSocket for real-time notifications |

## Prerequisites

- Java 17+
- Node.js 20+ (matches CI)
- MySQL 8
- npm

The backend uses the Maven Wrapper (`rms-backend/mvnw.cmd`), so a separate Maven install is not
required for normal local commands.

## Local development

### 1. Create your local environment file

```powershell
Copy-Item .env.example .env
```

Edit `.env` with your local MySQL credentials and a JWT secret. Generate a secret with:

```powershell
[Convert]::ToBase64String((1..64 | ForEach-Object { Get-Random -Minimum 0 -Maximum 256 } | ForEach-Object { [byte]$_ }))
```

### 2. Install root dependencies

```powershell
npm install
```

### 3. Start backend and frontend together

```powershell
npm run dev:all
```

Default URLs:

- Frontend: `http://localhost:5173`
- Backend: `http://localhost:8080`
- API base: `http://localhost:8080/api`

`dev:all` checks ports `8080` and `5173` first; if a port is busy it stops and prints the owning
process instead of killing anything.

**Port helpers:** `npm run dev:check-ports` (check) · `npm run dev:free-ports` (free, with
confirmation) · `npm run dev:free-ports:force` (free without asking).

**Run one side only:**

```powershell
cd rms-frontend; npm install; npm run dev      # frontend at :5173
cd rms-backend;  .\mvnw.cmd spring-boot:run     # backend at :8080
```

## Run with Docker

The whole stack (MySQL + backend + frontend) can run in containers with one command — no local
Java, Node, or MySQL needed, only Docker.

```bash
docker compose up --build -d        # first run downloads images + builds (~3-8 min)
```

Then open **http://localhost:3000** and log in with `admin` / `admin123` (other seeded users:
`dc`, `ddc`, `sc`, `asc`, `pma` — password `password123`). Stop with `docker compose down`
(keeps data) or `docker compose down -v` (wipes the database + uploads).

This dev stack runs the `dev` profile (seeds demo users) and serves a built frontend on `:3000`;
it is for local/demo use, not production. The full walkthrough — including the two gotchas, the
**offline image bundle** for air-gapped servers, and the production-style `docker-compose.prod.yml`
— is in **[QUICKSTART-DOCKER.md](QUICKSTART-DOCKER.md)** and **[DEPLOYMENT.md](DEPLOYMENT.md)** (Appendix A).

> For active coding, prefer `npm run dev:all` (hot reload). Docker runs a built snapshot, so it
> only reflects code changes after `docker compose up --build`. The two can't run at once — both
> use port `8080`.

## Environment variables

The backend reads these from `.env` locally, or from server environment variables in production.

| Variable | Required | Purpose |
|----------|----------|---------|
| `DB_USERNAME` | Yes | MySQL username |
| `DB_PASSWORD` | Yes | MySQL password |
| `JWT_SECRET` | Yes | Base64 secret used to sign JWTs (startup fails if missing) |
| `APP_UPLOAD_DIR` | Recommended | External folder for uploads. Defaults to `C:/customs_uploads` |
| `APP_CORS_ALLOWED_ORIGINS` | Production | Comma-separated allowed origins for HTTP **and** WebSocket. Defaults to localhost |
| `SPRING_PROFILES_ACTIVE` | Optional | Spring profile (`dev` locally, `prod` in production) |
| `JWT_EXPIRATION_MS` | Optional | Token lifetime. Defaults to `28800000` (8h) |
| `DC_AUTO_FORWARD_POLL_MS` | Optional | Auto-forward scheduler interval. Defaults to `60000` |
| `APP_SEED_ENABLED` | Local only | Enables default user seeding. **Keep `false` in production** |
| `APP_SEED_DEFAULT_PASSWORD` / `APP_SEED_ADMIN_PASSWORD` | Local only | Passwords for seeded users |

The frontend is built with `VITE_API_BASE_URL` (the backend's public base URL) baked in at build
time. Never commit `.env`, production passwords, or production JWT secrets.

## Seeded roles and default users

Roles (`ADMIN`, `DC`, `DDC`, `SDDC`, `SC`, `ASC`, `PMA`) and the permission matrix are seeded on
startup. Default **users** are seeded only under the `local`/`dev`/`e2e` profiles when
`APP_SEED_ENABLED=true` — never in `prod`. When enabled, usernames `dc`, `ddc`, `sc`, `asc`, `pma`,
and `admin` are created if missing, using `APP_SEED_DEFAULT_PASSWORD` / `APP_SEED_ADMIN_PASSWORD`.

> Production has no seeded users by design. See [DEPLOYMENT.md](DEPLOYMENT.md) for first-admin setup.

## Authentication and file downloads

Normal API requests use the JWT access token in the `Authorization: Bearer <token>` header.

Browser-rendered downloads (profile pictures and attachments opened in an `<img>`, `<iframe>`, or
new tab) use **short-lived scoped download tokens** instead:

- `POST /api/auth/me/profile-picture-token`
- `POST /api/attachments/{attachmentId}/download-token`

The returned URL carries a `download_token` (valid ~120s, scoped to that one resource), not the
main access token — so the long-lived JWT never appears in browser URLs, logs, or referrers.

## Real-time notifications

The frontend opens an authenticated WebSocket to `/ws/notifications` (the JWT is passed as a query
parameter, since browsers can't set WebSocket headers). The backend pushes events such as
`DOCUMENT_FORWARDED`, `DOCUMENT_RETURNED`, and `PERMISSIONS_UPDATED`, which drive in-app toasts,
browser notifications, and live permission refreshes. The WebSocket's allowed origins come from the
same `APP_CORS_ALLOWED_ORIGINS` setting as HTTP CORS.

## Testing

| Suite | Count | Command |
|-------|-------|---------|
| Backend (JUnit 5, H2) | 201 | `cd rms-backend; .\mvnw.cmd test` |
| Frontend unit (Vitest) | 100 | `npm --prefix rms-frontend run test:unit` |
| Frontend E2E (Playwright) | 19 | `cd rms-frontend; npm run test:e2e` |

Backend integration tests use the `test` profile with an in-memory H2 database in MySQL
compatibility mode, so they need no local MySQL. The Playwright E2E suite starts the real backend
against MySQL and needs these env vars:

```powershell
$env:RMS_E2E_ADMIN_USER="admin"; $env:RMS_E2E_ADMIN_PASS="your_admin_password"
$env:RMS_E2E_DC_USER="dc";       $env:RMS_E2E_DC_PASS="your_dc_password"
```

## Continuous integration

`.github/workflows/ci.yml` runs on every push and PR:

- **Backend tests** against the in-memory H2 database.
- **Frontend unit + Playwright E2E** against an ephemeral MySQL service container (E2E data does
  not persist after the job).

## Deployment

Customs RMS deploys as two artifacts behind a reverse proxy (Nginx recommended):

- **Backend** — the Spring Boot JAR (`rms-backend-0.0.1-SNAPSHOT.jar`) on port `8080`.
- **Frontend** — the static Vite build (`rms-frontend/dist/`) served by the web server.

```text
Users -> Nginx (TLS) -> /        -> Vue static files
                        /api/     -> backend :8080
                        /ws/      -> backend :8080  (WebSocket upgrade)
                                     -> MySQL + upload directory
```

Three settings cause silent production failures if missed: **`APP_CORS_ALLOWED_ORIGINS`** (your
real domain — governs both the API and the WebSocket), **`VITE_API_BASE_URL`** (set before building
the frontend), and **`SPRING_PROFILES_ACTIVE=prod`** (switches to schema `validate` and disables
seeding). The reverse proxy must also forward the WebSocket upgrade headers for `/ws/`.

**The full, step-by-step guide is in [DEPLOYMENT.md](DEPLOYMENT.md)** — environment setup, database,
systemd + Nginx config (including the WebSocket block and a login rate-limit), first-admin
bootstrap, security hardening, rollback, and troubleshooting. Verify each release against
[RELEASE_CHECKLIST.md](RELEASE_CHECKLIST.md) before going live.

## Further documentation

- **[Customs-RMS-System-Documentation.docx](Customs-RMS-System-Documentation.docx)** — full system documentation:
  architecture, complete data model, security and permission model, workflow, API reference, and
  maintenance/handover notes. Start here for the internals ([PDF copy](Customs-RMS-System-Documentation.pdf)).
- [QUICKSTART-DOCKER.md](QUICKSTART-DOCKER.md) — run the whole stack in Docker with one command.
- [DEPLOYMENT.md](DEPLOYMENT.md) — production deployment guide (Appendix A covers Docker + bundles).
- [packaging/README.md](packaging/README.md) — recipient guide shipped inside the offline image bundle.
- [RELEASE_CHECKLIST.md](RELEASE_CHECKLIST.md) — pre-release verification checklist.
- [rms-backend/README.md](rms-backend/README.md) — backend configuration and secrets handling.
