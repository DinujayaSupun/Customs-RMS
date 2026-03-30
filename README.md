# Customs RMS

Document Records Management System for Sri Lanka Customs.

![Java](https://img.shields.io/badge/Java-17-blue?logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.2-brightgreen?logo=springboot)
![Vue](https://img.shields.io/badge/Vue-3-41b883?logo=vuedotjs)
![MySQL](https://img.shields.io/badge/MySQL-8-orange?logo=mysql)

---

## Overview

Customs RMS is an internal workflow and document records management system built for Sri Lanka Customs.

The system supports:

* Secure login with JWT authentication
* Role-based access and permission management
* Document workflow routing between officers
* Remarks and minute tracking
* Attachment upload and download
* Audit logs and action history
* User administration
* Profile management
* Auto-forwarding for unattended assignments

The system is designed for internal use by customs officers and administrators.

---

## Tech Stack

| Layer          | Technology                                                   |
| -------------- | ------------------------------------------------------------ |
| Backend        | Spring Boot 4.0.2, Java 17, Spring Security, Spring Data JPA |
| Frontend       | Vue 3, Vite, Vue Router                                      |
| Database       | MySQL 8                                                      |
| Authentication | JWT                                                          |
| Build Tools    | Maven, npm                                                   |
| Testing        | Playwright, JUnit                                            |
| CI/CD          | GitHub Actions                                               |

---

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

---

## Main Features

* JWT authentication with Spring Security
* Role + permission matrix authorization
* Document create/list/details/edit/archive flow
* Workflow actions:

  * Forward
  * Return
  * Approve
  * Reject
  * Issue
  * Reopen
* Public/private forwarding visibility rules
* Document remarks/minutes timeline
* Attachment upload/download/versioning/delete
* Inbox and sent message views
* Audit logs with filtering and CSV export
* Admin user management:

  * Create users
  * Edit users
  * Activate/deactivate users
  * Reset passwords
  * Merge users
  * Export users
* Profile management:

  * Personal details
  * Password change
  * Profile picture upload
* DC auto-forward scheduler for unattended assignments

---

## Supported Roles

* ADMIN
* DC
* DDC
* SDDC
* SC
* ASC
* PMA

---

## Backend API Areas

* `/api/health`
* `/api/auth`
* `/api/documents`
* `/api/reports` (legacy compatibility)
* `/api/documents/{id}/remarks`
* `/api/documents/{id}/movements`
* `/api/documents/{id}/attachments`
* `/api/attachments/{id}`
* `/api/audit-logs`
* `/api/admin/users`
* `/api/admin/permissions`

---

## Prerequisites

Before running the project, install:

* Java 17+
* Maven 3.9+
* Node.js 18+ (Node 20 recommended)
* MySQL 8
* Git

Recommended IDEs:

* VS Code
* IntelliJ IDEA
* MySQL Workbench

---

## Required Environment Variables

The backend uses environment variables for configuration.

| Variable       | Description                 | Example                                        |
| -------------- | --------------------------- | ---------------------------------------------- |
| DB_USERNAME    | MySQL username              | customs_rms_user                               |
| DB_PASSWORD    | MySQL password              | strongPassword123                              |
| JWT_SECRET     | Base64 encoded JWT secret   | long-secret-value                              |
| APP_UPLOAD_DIR | Attachment upload directory | C:/customs_uploads                             |
| FRONTEND_URL   | Allowed frontend URL        | [http://localhost:5173](http://localhost:5173) |

---

## Database Setup

### 1. Create Database

```sql
CREATE DATABASE customs_rms;
```

### 2. Create Dedicated Database User

```sql
CREATE USER 'customs_rms_user'@'localhost' IDENTIFIED BY 'strongPassword123';
GRANT ALL PRIVILEGES ON customs_rms.* TO 'customs_rms_user'@'localhost';
FLUSH PRIVILEGES;
```

### 3. Configure Environment Variables

Example:

```powershell
setx DB_USERNAME "customs_rms_user"
setx DB_PASSWORD "strongPassword123"
setx JWT_SECRET "PUT_BASE64_SECRET_HERE"
setx APP_UPLOAD_DIR "C:/customs_uploads"
setx FRONTEND_URL "http://localhost:5173"
```

---

## Local Development Run

### 1. Start Backend

```powershell
cd rms-backend
mvn spring-boot:run
```

Backend default URL:

```text
http://localhost:8080
```

Notes:

* Default DB URL comes from `application.properties`
* Upload directory default is `C:/customs_uploads`
* Local template exists at:

```text
rms-backend/src/main/resources/application-local.example.properties
```

* Hibernate SQL logging is disabled by default:

```text
spring.jpa.show-sql=false
```

### 2. Start Frontend

```powershell
cd rms-frontend
npm install
npm run dev
```

Frontend default URL:

```text
http://localhost:5173
```

Frontend API base URL:

```text
http://localhost:8080/api
```

### 3. Start Backend + Frontend Together

From repository root:

```powershell
npm install
npm run dev:all
```

This starts:

* Backend via Maven Wrapper
* Frontend via Vite

Stop both using:

```text
Ctrl + C
```

---

## Default Seeded Users

These accounts are intended for local development only.

| Username | Password  | Role  |
| -------- | --------- | ----- |
| dc       | Pass@123  | DC    |
| ddc      | Pass@123  | DDC   |
| sc       | Pass@123  | SC    |
| asc      | Pass@123  | ASC   |
| pma      | Pass@123  | PMA   |
| admin    | Admin@123 | ADMIN |

Important:

* Change all default passwords immediately in production
* Do not expose seeded passwords in production environments

---

## Testing

### Backend Tests

```powershell
cd rms-backend
mvn test
```

### Frontend E2E Tests

```powershell
cd rms-frontend
npm run test:e2e
```

---

## CI/CD

GitHub Actions workflow is available at:

```text
.github/workflows/ci.yml
```

The CI pipeline runs:

* Backend tests
* Frontend Playwright smoke tests
* Temporary MySQL container for automated testing

---

## Build for Production

### Backend

```powershell
cd rms-backend
mvn clean package -DskipTests
java -jar target/rms-backend-0.0.1-SNAPSHOT.jar
```

### Frontend

```powershell
cd rms-frontend
npm run build
```

Frontend production build output:

```text
rms-frontend/dist/
```

---

## Recommended Production Deployment

### Backend

* Deploy Spring Boot JAR on Windows Server or Linux Server
* Use Java 17 runtime
* Use environment variables for secrets

### Frontend

* Deploy Vue frontend using IIS, Nginx, or Apache
* Serve the built `dist` folder

### Database

* Host MySQL on dedicated server or internal DB server
* Use restricted DB credentials
* Schedule automatic backups

### Upload Storage

* Store uploaded files in a secure server folder
* Restrict direct public access to uploads
* Back up upload folder daily

---

## Backup and Restore

Recommended backup schedule:

* Database backup: daily
* Upload folder backup: daily
* Full server backup: weekly

Example MySQL backup command:

```powershell
mysqldump -u customs_rms_user -p customs_rms > customs_rms_backup.sql
```

Example restore command:

```powershell
mysql -u customs_rms_user -p customs_rms < customs_rms_backup.sql
```

---

## Browser Support

Supported browsers:

* Google Chrome
* Microsoft Edge
* Mozilla Firefox

Recommended resolution:

```text
1366x768 or higher
```

---

## Security Notes

* Use HTTPS in production
* Use strong JWT secrets
* Change default passwords immediately
* Restrict upload folder permissions
* Use dedicated DB users instead of root
* Keep server firewall enabled
* Limit backend access to internal company network when possible
* Enable antivirus scanning for uploaded files

---

## Troubleshooting

### Backend does not start

Check:

* Java version
* Maven installation
* Database connection
* Environment variables

### Frontend cannot connect to backend

Check:

* Backend is running on port 8080
* Frontend API URL is correct
* Browser console for CORS errors

### File uploads fail

Check:

* Upload folder exists
* Upload folder permissions are correct
* Disk has enough free space

### Login fails

Check:

* Database seeded users exist
* JWT secret is configured
* User account is active

---

## License

Internal project for Sri Lanka Customs.
Not intended for public distribution.
