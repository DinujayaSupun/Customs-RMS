# Customs RMS

> Document Records Management System for Sri Lanka Customs

![Java](https://img.shields.io/badge/Java-17-blue?logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.2-brightgreen?logo=springboot)
![Vue](https://img.shields.io/badge/Vue-3-41b883?logo=vuedotjs)
![MySQL](https://img.shields.io/badge/MySQL-8-orange?logo=mysql)

---

## Overview

Customs RMS is an internal web application for managing customs-related documents. It handles document intake, inter-department movement, attachments, remarks, and provides a full audit trail with role-based access control.

---

## Features

- 📄 Document management — create, view, track status
- 🔄 Document movements between departments
- 📎 Attachments and remarks per document
- 👥 User management with role-based access control
- 📋 Audit log with filters and CSV export
- 🔐 JWT-based authentication

---

## Roles

| Role | Description |
|------|-------------|
| `ADMIN` | Full system access, user management |
| `DC` | Director of Customs |
| `DDC` | Deputy Director of Customs |
| `SC` | Superintendent of Customs |
| `ASC` | Assistant Superintendent of Customs |
| `PMA` | Port Management Officer |

---

## Tech Stack

| Layer | Technology |
|-------|------------|
| Backend | Spring Boot 4.0.2, Java 17 |
| Frontend | Vue 3, Vite, Tailwind CSS |
| Database | MySQL 8 |
| Auth | JWT (stateless) |

---

## Project Structure

```
Customs-RMS/
├── rms-backend/    # Spring Boot REST API
└── rms-frontend/   # Vue 3 SPA
```

---

## Getting Started

### Prerequisites

- Java 17+
- Node.js 18+
- MySQL 8
- Maven 3.9+

---

### Backend Setup

**1. Set environment variables**

```powershell
setx JWT_SECRET "your_base64_encoded_secret"
setx DB_USERNAME "root"
setx DB_PASSWORD "your_password"
setx APP_UPLOAD_DIR "C:/customs_uploads"
```

> `JWT_SECRET` is required — the backend will not start without it.

**2. Run the backend**

```bash
cd rms-backend
mvn spring-boot:run
```

Runs on **http://localhost:8080**

> See [rms-backend/README.md](rms-backend/README.md) for advanced config options.

---

### Frontend Setup

```bash
cd rms-frontend
npm install
npm run dev
```

Runs on **http://localhost:5173**

---

## Default Login (Development)

| Username | Password | Role |
|----------|----------|------|
| `admin` | `Admin@123` | ADMIN |

> Default users are seeded on first startup. Change passwords before deploying to production.

---

## Configuration

| Variable | Description | Default |
|----------|-------------|---------|
| `JWT_SECRET` | Base64 JWT signing secret | *(required)* |
| `DB_USERNAME` | MySQL username | `root` |
| `DB_PASSWORD` | MySQL password | `root123` |
| `APP_UPLOAD_DIR` | File upload directory | `C:/customs_uploads` |

---

## Build for Production

**Backend**
```bash
cd rms-backend
mvn clean package -DskipTests
java -jar target/rms-backend-0.0.1-SNAPSHOT.jar
```

**Frontend**
```bash
cd rms-frontend
npm run build
# Output in rms-frontend/dist/
```
