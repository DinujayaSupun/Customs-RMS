# Customs RMS

Document Records Management System for Sri Lanka Customs.

![Java](https://img.shields.io/badge/Java-17-blue?logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.2-brightgreen?logo=springboot)
![Vue](https://img.shields.io/badge/Vue-3-41b883?logo=vuedotjs)
![MySQL](https://img.shields.io/badge/MySQL-8-orange?logo=mysql)

## Overview

Customs RMS is an internal workflow system for receiving, routing, reviewing, and completing customs-related documents. It supports document ownership transfer between officers, document visibility rules, file attachments, workflow minutes, audit logs, and admin-managed role permissions.

## Current Feature Set

- JWT-based authentication with Spring Security
- Role-based access control backed by a permission matrix
- Document creation, viewing, updating, soft deletion, and search
- Workflow actions: forward, return, approve, reject, issue, reopen
- Private/public forwarding visibility rules
- Remarks/minutes on documents and during workflow actions
- Attachment upload, download, preview, version history, and deletion
- Inbox view for received documents and sent-messages tracking
- Audit log listing and CSV export
- User profile management, profile picture upload, and password change
- Admin user management: create, edit, activate, deactivate, reset password, merge duplicates, CSV export
- DC auto-forward escalation when assigned documents are not opened in time

## Tech Stack

| Layer | Technology |
|-------|------------|
| Backend | Spring Boot 4.0.2, Java 17, Spring Security, Spring Data JPA |
| Frontend | Vue 3, Vite, Vue Router |
| Database | MySQL 8 |
| Auth | JWT |
| Build Tools | Maven, npm |

## Project Structure

```text
Customs-RMS/
├── rms-backend/     # Spring Boot REST API
├── rms-frontend/    # Vue 3 SPA
├── README.md
└── note.txt
```

## Backend Summary

The backend exposes APIs for:

- `/api/auth` for login, profile, password change, profile picture, and user lookup
- `/api/documents` for document CRUD, workflow actions, inbox/sent data, and workload stats
- `/api/documents/{id}/remarks` for minutes
- `/api/documents/{id}/movements` for workflow history
- `/api/documents/{id}/attachments` and `/api/attachments/{id}` for file handling
- `/api/audit-logs` for audit search and export
- `/api/admin/users` for admin user management
- `/api/admin/permissions` for permission matrix and DC auto-forward configuration

Legacy `/api/reports` document routes are still supported for backward compatibility.

## Frontend Summary

The frontend currently includes these main screens:

- Login
- Inbox
- Documents
- Document Details
- My Profile
- Logs
- Users
- Permissions

Navigation and route guards are driven by the authenticated user and their permissions.

## Prerequisites

- Java 17+
- Maven 3.9+
- Node.js 18+
- MySQL 8

## Backend Setup

From the repository root:

```powershell
cd rms-backend
```

Set environment variables:

```powershell
setx JWT_SECRET "PUT_BASE64_SECRET_HERE"
setx DB_USERNAME "root"
setx DB_PASSWORD "your_password"
setx APP_UPLOAD_DIR "C:/customs_uploads"
```

Then open a new terminal and run:

```powershell
mvn spring-boot:run
```

Backend default URL:

```text
http://localhost:8080
```

Notes:

- The app uses MySQL database `customs_rms` with `createDatabaseIfNotExist=true`
- File uploads are stored outside the project using `APP_UPLOAD_DIR`
- A local profile template is available at [rms-backend/src/main/resources/application-local.example.properties](/C:/Users/Dinujaya/Documents/GitHub/Customs-RMS/rms-backend/src/main/resources/application-local.example.properties)

## Frontend Setup

From the repository root:

```powershell
cd rms-frontend
npm install
npm run dev
```

Frontend default URL:

```text
http://localhost:5173
```

Current frontend API calls target the backend at `http://localhost:8080/api`.

## Seeded Roles and Users

The backend seeds these roles:

- `ADMIN`
- `DC`
- `DDC`
- `SDDC`
- `SC`
- `ASC`
- `PMA`

Default seeded users:

| Username | Password | Role |
|----------|----------|------|
| `dc` | `Pass@123` | `DC` |
| `ddc` | `Pass@123` | `DDC` |
| `sc` | `Pass@123` | `SC` |
| `asc` | `Pass@123` | `ASC` |
| `pma` | `Pass@123` | `PMA` |
| `admin` | `Admin@123` | `ADMIN` |

## Important Configuration

| Variable | Description | Default |
|----------|-------------|---------|
| `JWT_SECRET` | Base64 JWT signing secret | Fallback exists in local config, but you should override it |
| `DB_USERNAME` | MySQL username | `root` |
| `DB_PASSWORD` | MySQL password | `root123` |
| `APP_UPLOAD_DIR` | External file upload directory | `C:/customs_uploads` |
| `DC_AUTO_FORWARD_POLL_MS` | Scheduler polling interval for DC auto-forward | `60000` |

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

The frontend build output is written to `rms-frontend/dist/`.

## Known Notes

- Spring Security is enabled in the current codebase
- Authentication is JWT-based and most routes require a valid token
- Admin-only features are enforced both in the backend and frontend route guards
- Document workflow rules are implemented in backend service logic, not only in the UI
