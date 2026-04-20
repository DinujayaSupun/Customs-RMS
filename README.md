# Customs RMS

Document Records Management System for Sri Lanka Customs.

![Java](https://img.shields.io/badge/Java-17-blue?logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.2-brightgreen?logo=springboot)
![Vue](https://img.shields.io/badge/Vue-3-41b883?logo=vuedotjs)
![MySQL](https://img.shields.io/badge/MySQL-8-orange?logo=mysql)

## Overview

Customs RMS is an internal workflow system for receiving, routing, reviewing, and completing customs documents. It supports role-based visibility, document movement between officers, remarks/minutes, attachments, audit logs, and admin permission management.

## Tech Stack

| Layer | Technology |
|-------|------------|
| Backend | Spring Boot 4.0.2, Java 17, Spring Security, Spring Data JPA |
| Frontend | Vue 3, Vite, Vue Router |
| Database | MySQL 8 |
| Auth | JWT |
| Build/Test | Maven, npm, Playwright |

## Project Structure

```text
Customs-RMS/
├── .github/workflows/         # CI pipeline
├── package.json               # Root dev orchestration scripts
├── rms-backend/               # Spring Boot REST API
├── rms-frontend/              # Vue 3 SPA + Playwright E2E
├── README.md
└── note.txt
```

## Main Features

- JWT authentication with Spring Security
- Role + permission matrix authorization
- Document create/list/details/edit/archive flow
- Workflow actions: forward, return, approve, reject, issue, reopen
- Public/private forwarding visibility rules
- Document remarks/minutes timeline
- Attachment upload/download/versioning/delete
- Inbox and sent messages views
- Audit logs with filtering and CSV export
- Admin user management: create/edit/activate/deactivate/reset password/merge/export
- Profile management: personal details, password change, profile picture
- DC auto-forward scheduler for unattended assignments

## Backend API Areas

- `/api/health`
- `/api/auth`
- `/api/documents` (also supports legacy `/api/reports`)
- `/api/documents/{id}/remarks`
- `/api/documents/{id}/movements`
- `/api/documents/{id}/attachments`
- `/api/attachments/{id}`
- `/api/audit-logs`
- `/api/admin/users`
- `/api/admin/permissions`

## Prerequisites

- Java 17+
- Maven 3.9+
- Node.js 18+ (Node 20 recommended for CI alignment)
- MySQL 8

## Local Run

### 1) Create local environment file

```powershell
Copy-Item .env.example .env
```

Edit `.env` with your local DB password and a Base64 JWT secret. The `.env` file is ignored by Git.

Generate a local JWT secret:

```powershell
[Convert]::ToBase64String((1..64 | ForEach-Object { Get-Random -Minimum 0 -Maximum 256 } | ForEach-Object { [byte]$_ }))
```

### 2) Start backend and frontend

```powershell
npm run dev:all
```

Backend default URL: `http://localhost:8080`
Frontend default URL: `http://localhost:5173`

Notes:
- DB URL default is `jdbc:mysql://localhost:3306/customs_rms?...` from `application.properties`
- `DB_USERNAME`, `DB_PASSWORD`, and `JWT_SECRET` are required through `.env` or server environment variables
- Upload directory default is `C:/customs_uploads`
- Local template: `rms-backend/src/main/resources/application-local.example.properties`
- Hibernate SQL console spam is disabled by default (`spring.jpa.show-sql=false`) to keep terminal logs readable

### 3) Start frontend only

```powershell
cd rms-frontend
npm install
npm run dev
```

Frontend default URL: `http://localhost:5173`  
Frontend calls backend at `http://localhost:8080/api`.

### 4) Start backend + frontend together (single command)

From repository root:

```powershell
npm install
npm run dev:all
```

This starts:
- backend via Maven Wrapper (`rms-backend\\mvnw.cmd -f rms-backend/pom.xml spring-boot:run`)
- frontend via `npm --prefix rms-frontend run dev`

`dev:all` sets `MAVEN_USER_HOME` to a repo-local `.m2` folder for consistent wrapper behavior.

`dev:all` now prints a clear URL block before logs begin:
- Frontend: `http://localhost:5173`
- Backend: `http://localhost:8080`

Stop both with `Ctrl + C`.

If logs scroll too fast, increase terminal history/scrollback size (for example in Windows Terminal profile settings).

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

## Testing

### Backend tests

```powershell
cd rms-backend
mvn test
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
- backend tests
- frontend Playwright smoke tests

CI uses an ephemeral MySQL service container, so temporary E2E data created during a run does not persist after the job ends.

## Build for Production

Backend:

```powershell
cd rms-backend
mvn clean package -DskipTests
java -jar target/rms-backend-0.0.1-SNAPSHOT.jar
```

Frontend:

```powershell
cd rms-frontend
npm run build
```

Frontend output: `rms-frontend/dist/`
