# Customs RMS

Document Records Management System for Sri Lanka Customs.

![Java](https://img.shields.io/badge/Java-17-blue?logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.2-brightgreen?logo=springboot)
![Vue](https://img.shields.io/badge/Vue-3-41b883?logo=vuedotjs)
![MySQL](https://img.shields.io/badge/MySQL-8-orange?logo=mysql)

## Overview

Customs RMS is an internal workflow system for receiving, routing, reviewing, and completing customs documents. It supports role-based visibility, document movement between officers, remarks/minutes, attachments, audit logs, inbox/sent-message views, and admin permission management.

## Tech Stack

| Layer | Technology |
|-------|------------|
| Backend | Spring Boot 4.0.2, Java 17, Spring Security, Spring Data JPA |
| Frontend | Vue 3, Vite, Vue Router |
| Database | MySQL 8 |
| Auth | JWT access tokens plus short-lived scoped download tokens |
| Build/Test | Maven Wrapper, npm, Vitest, Playwright |

## Project Structure

```text
Customs-RMS/
|-- .github/workflows/         # CI pipeline
|-- package.json               # Root dev orchestration scripts
|-- scripts/                   # Local development helper scripts
|-- rms-backend/               # Spring Boot REST API
|-- rms-frontend/              # Vue 3 SPA
|-- README.md
`-- note.txt
```

## Main Features

- JWT authentication with Spring Security.
- Role and permission matrix authorization.
- Document create, list, details, edit, archive, and visibility flows.
- Workflow actions: forward, return, approve, reject, done/issue, and reopen.
- Public/private forwarding visibility rules.
- Document remarks/minutes timeline.
- Attachment upload, download, versioning, and delete.
- Short-lived signed download URLs for browser-rendered files and profile pictures.
- Inbox and sent messages views.
- Undo Send workflow controls.
- Audit logs with filtering and CSV export.
- Admin user management: create, edit, activate, deactivate, reset password, merge, and export.
- Profile management: personal details, password change, and profile picture.
- DC auto-forward scheduler for unattended assignments.

## System Modules

| Module | Path | Purpose |
|--------|------|---------|
| Backend API | `rms-backend/` | Spring Boot REST API, authentication, workflow rules, file upload, audit logging |
| Frontend UI | `rms-frontend/` | Vue 3 single-page application used by Customs RMS users |
| Root scripts | `package.json` | One-command local development orchestration |
| Dev scripts | `scripts/` | Port checks and local environment startup helpers |
| CI | `.github/workflows/ci.yml` | Backend and frontend automated test workflow |

## Request Flow Architecture

Most user actions follow the same path through the system:

```text
Vue page -> frontend API wrapper -> Spring controller -> service -> repository -> database table
```

Example document list flow:

```text
rms-frontend/src/pages/DocumentsPage.vue
  -> rms-frontend/src/api/documents.api.js
  -> rms-backend/src/main/java/lk/customs/rms/controller/DocumentController.java
  -> rms-backend/src/main/java/lk/customs/rms/service/DocumentService.java
  -> rms-backend/src/main/java/lk/customs/rms/service/impl/DocumentServiceImpl.java
  -> rms-backend/src/main/java/lk/customs/rms/repository/DocumentRepository.java
  -> documents table
```

The same pattern applies to most modules:

| Area | Frontend Entry | API Wrapper | Backend Controller | Service | Repository/Table |
|------|----------------|-------------|--------------------|---------|------------------|
| Login/profile | `LoginPage.vue`, `ProfilePage.vue`, `AppLayout.vue` | `auth.api.js` | `AuthController` | Spring Security services, `FileStorageService` | `UserRepository`, `users` |
| Documents | `DocumentsPage.vue`, `DocumentDetailsPage.vue` | `documents.api.js` | `DocumentController` | `DocumentServiceImpl` | `DocumentRepository`, `documents` |
| Inbox | `InboxPage.vue` | `documents.api.js` | `DocumentController` | `DocumentServiceImpl` | `documents`, `document_movements`, `document_user_views` |
| Minutes/remarks | `DocumentDetailsPage.vue`, `InboxPage.vue` | `documents.api.js` | `DocumentRemarkController` | `DocumentServiceImpl` for workflow minutes | `DocumentRemarkRepository`, `document_remarks` |
| Movements/history | `DocumentDetailsPage.vue`, `InboxPage.vue` | `documents.api.js` | `DocumentMovementController` | Direct repository mapping with permission checks | `DocumentMovementRepository`, `document_movements` |
| Attachments | `DocumentDetailsPage.vue`, `DocumentsPage.vue`, `InboxPage.vue` | `documents.api.js` | `DocumentAttachmentController` | `AttachmentServiceImpl`, `FileStorageService` | `DocumentAttachmentRepository`, upload folder |
| Audit logs | `LogsPage.vue` | `logs.api.js` | `LogsController`, `AuditLogController` | `AuditLogServiceImpl` | `AuditLogRepository`, `audit_logs` |
| Admin users | `UsersPage.vue` | `auth.api.js` | `AdminUserController` | `AdminUserServiceImpl` | `UserRepository`, `RoleRepository` |
| Permissions | `PermissionsPage.vue` | `permissions.api.js` | `AdminPermissionController` | `PermissionServiceImpl`, `DcAutoForwardConfigServiceImpl` | `RolePermissionRepository`, `dc_auto_forward_config` |

Responsibilities are split like this:

- Pages own screen state, form state, and user interactions.
- Frontend API wrappers own HTTP paths and request/response calls.
- Controllers own route definitions, authentication lookup, and request validation boundaries.
- Services own business rules, permissions, workflow transitions, audit logging, and file handling.
- Repositories own database reads/writes.
- DTOs define the API shape sent to the frontend.

When adding a feature, start by identifying the user workflow, then follow the flow above. A new document action usually needs a Vue button, an API wrapper function, a controller route, service permission/business logic, repository access, audit logging, and focused tests.

## Backend API Areas

- `/api/health`
- `/api/auth`
- `/api/documents` (also supports legacy `/api/reports`)
- `/api/documents/my-inbox`
- `/api/documents/my-workload-stats`
- `/api/documents/{id}/remarks`
- `/api/documents/{id}/movements`
- `/api/documents/{id}/attachments`
- `/api/attachments/{id}`
- `/api/audit-logs`
- `/api/admin/users`
- `/api/admin/permissions`

## Prerequisites

- Java 17+
- Node.js 18+ (Node 20 recommended for CI alignment)
- MySQL 8
- npm

The backend uses the Maven Wrapper in `rms-backend/mvnw.cmd`, so a separate Maven install is not required for normal local commands.

## Environment Variables

The backend reads these values from `.env` during local development or from server environment variables in production.

| Variable | Required | Purpose |
|----------|----------|---------|
| `DB_USERNAME` | Yes | MySQL username |
| `DB_PASSWORD` | Yes | MySQL password |
| `JWT_SECRET` | Yes | Base64 encoded secret used to sign JWT tokens |
| `APP_UPLOAD_DIR` | Recommended | External folder for document/profile uploads. Defaults to `C:/customs_uploads` |
| `DC_AUTO_FORWARD_POLL_MS` | Optional | DC auto-forward scheduler interval. Defaults to `60000` |
| `SPRING_PROFILES_ACTIVE` | Optional | Spring profile, for example `dev` locally |
| `APP_SEED_ENABLED` | Local only | Enables default user seeding. Keep disabled in production |
| `APP_SEED_DEFAULT_PASSWORD` | Local only | Password for local seeded non-admin users |
| `APP_SEED_ADMIN_PASSWORD` | Local only | Password for local seeded admin user |

Do not commit `.env`, production passwords, or production JWT secrets.

Generate a local JWT secret:

```powershell
[Convert]::ToBase64String((1..64 | ForEach-Object { Get-Random -Minimum 0 -Maximum 256 } | ForEach-Object { [byte]$_ }))
```

## Local Development

### 1. Create local environment file

```powershell
Copy-Item .env.example .env
```

Edit `.env` with your local database credentials and JWT secret.

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

`dev:all` now checks ports `8080` and `5173` before starting. If a port is already occupied, startup stops and prints the PID/process name instead of killing anything.

### Dev Port Utilities

```powershell
npm run dev:check-ports
```

Checks whether ports `8080` and `5173` are available.

```powershell
npm run dev:free-ports
```

Shows the processes using the dev ports and asks you to type `YES` before stopping them.

```powershell
npm run dev:free-ports:force
```

Stops processes on the dev ports without asking. Use only when you are sure the listed ports belong to this project.

### Start Frontend Only

```powershell
cd rms-frontend
npm install
npm run dev
```

Frontend default URL: `http://localhost:5173`

### Start Backend Only

```powershell
cd rms-backend
.\mvnw.cmd spring-boot:run
```

Backend default URL: `http://localhost:8080`

## Seeded Roles and Optional Default Users

Roles:

- `ADMIN`
- `DC`
- `DDC`
- `SDDC`
- `SC`
- `ASC`
- `PMA`

Default user seeding is only active for local/dev profile usage when `APP_SEED_ENABLED=true`.

When enabled, these usernames are created if missing:

- `dc`
- `ddc`
- `sc`
- `asc`
- `pma`
- `admin`

Passwords come from local-only env values:

- `APP_SEED_DEFAULT_PASSWORD`
- `APP_SEED_ADMIN_PASSWORD`

## Authentication And File Downloads

Normal API requests use the JWT access token in the `Authorization: Bearer <token>` header.

Browser-rendered downloads, such as profile pictures and attachments opened in an `<img>`, `<iframe>`, or new tab, use short-lived scoped download tokens:

- `POST /api/auth/me/profile-picture-token`
- `POST /api/attachments/{attachmentId}/download-token`

The returned URL contains `download_token`, not the main JWT access token. This avoids exposing the long-lived access token in browser URLs, logs, or referrers.

## Testing

### Backend compile and test compile

```powershell
cd rms-backend
.\mvnw.cmd compile
.\mvnw.cmd test-compile
```

### Backend focused unit tests

```powershell
cd rms-backend
.\mvnw.cmd test "-Dtest=AuditLogServiceImplTests"
.\mvnw.cmd test "-Dtest=DocumentResponseMappingTests"
.\mvnw.cmd test "-Dtest=ResponseBatchMappingTests"
```

### Backend integration tests

```powershell
cd rms-backend
.\mvnw.cmd test
```

Integration tests use the `test` Spring profile with an in-memory H2 database in MySQL compatibility mode. They do not require a local MySQL username or password. E2E runs still use MySQL because they start the application closer to production.

### Frontend unit tests

```powershell
npm --prefix rms-frontend run test:unit
```

### Frontend E2E smoke tests

```powershell
cd rms-frontend
setx RMS_E2E_ADMIN_USER "admin"
setx RMS_E2E_ADMIN_PASS "your_admin_password"
setx RMS_E2E_DC_USER "dc"
setx RMS_E2E_DC_PASS "your_dc_password"
npm run test:e2e
```

## CI

GitHub Actions workflow is at `.github/workflows/ci.yml` and runs:

- backend tests with the in-memory test database
- frontend Playwright smoke tests

CI uses an ephemeral MySQL service container only for the frontend E2E smoke job, so temporary E2E data created during a run does not persist after the job ends.

## Build For Production

Backend:

```powershell
cd rms-backend
.\mvnw.cmd clean package -DskipTests
java -jar target/rms-backend-0.0.1-SNAPSHOT.jar
```

Frontend:

```powershell
cd rms-frontend
npm install
npm run build
```

Frontend output: `rms-frontend/dist/`

## Hosting / Production Deployment

For production, host the system as two applications:

- Backend: Spring Boot API running on port `8080`
- Frontend: static Vue build from `rms-frontend/dist/`, served by Nginx, Apache, IIS, or another web server

Recommended production layout:

```text
Internet / Intranet Users
        |
        v
Web Server / Reverse Proxy
        |
        |-- /           -> Vue frontend static files
        |-- /api/       -> Spring Boot backend on localhost:8080/api/
        `-- /actuator/  -> Optional health endpoints, restrict if exposed
```

### Production Steps

1. Prepare a server with Java 17+, Node.js, MySQL 8, and a web server such as Nginx.
2. Create the production MySQL database/user.
3. Set production environment variables: `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, and `APP_UPLOAD_DIR`.
4. Keep `APP_SEED_ENABLED=false` in production.
5. Build the backend with `.\mvnw.cmd clean package -DskipTests`.
6. Run the backend JAR as a managed service.
7. Build the frontend with `npm install` and `npm run build`.
8. Copy `rms-frontend/dist/` to the web server's public directory.
9. Configure the web server to serve the frontend and proxy `/api/` to `http://127.0.0.1:8080/api/`.
10. Add HTTPS with a valid certificate before production use.

Example Nginx configuration:

```nginx
server {
  listen 80;
  server_name your-domain.example;

  root /var/www/customs-rms/dist;
  index index.html;

  location / {
    try_files $uri $uri/ /index.html;
  }

  location /api/ {
    proxy_pass http://127.0.0.1:8080/api/;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
  }
}
```

## Production Checklist

- Use a strong Base64 `JWT_SECRET`.
- Use a dedicated MySQL user with only the permissions needed by the app.
- Store uploads outside the repository using `APP_UPLOAD_DIR`.
- Back up the database and upload directory regularly.
- Disable local seed users in production.
- Serve the frontend over HTTPS.
- Restrict server access with firewall rules.
- Keep `.env` and production credentials private.
- Run backend as a managed service so it restarts automatically after reboot.
- Test login, document upload/download, forwarding, audit logs, and admin user management before release.
