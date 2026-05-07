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

## System Modules

| Module | Path | Purpose |
|--------|------|---------|
| Backend API | `rms-backend/` | Spring Boot REST API, authentication, workflow rules, file upload, audit logging |
| Frontend UI | `rms-frontend/` | Vue 3 single-page application used by Customs RMS users |
| Root scripts | `package.json` | One-command local development orchestration |
| CI | `.github/workflows/ci.yml` | Backend and frontend automated test workflow |

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
        |-- /api/       -> Spring Boot backend on localhost:8080
        |-- /actuator/  -> Optional health endpoints, restrict if exposed
```

### Production Steps

1. Prepare a server with Java 17+, Node.js, Maven, MySQL 8, and a web server such as Nginx.
2. Create the production MySQL database/user.
3. Set production environment variables: `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, and `APP_UPLOAD_DIR`.
4. Keep `APP_SEED_ENABLED=false` in production.
5. Build the backend:

```powershell
cd rms-backend
mvn clean package -DskipTests
```

6. Run the backend JAR as a service:

```powershell
java -jar target/rms-backend-0.0.1-SNAPSHOT.jar
```

7. Build the frontend:

```powershell
cd rms-frontend
npm install
npm run build
```

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
- Test login, document upload, forwarding, audit logs, and admin user management before release.
