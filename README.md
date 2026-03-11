# Customs RMS

Document Records Management System for Sri Lanka Customs.
Manages document intake, movement, attachments, remarks, and audit logging
with role-based access control.

## Tech Stack
- Backend: Spring Boot 4.0.2 · Java 17 · MySQL 8
- Frontend: Vue 3 · Vite · Tailwind CSS
- Auth: JWT (stateless)

## Roles
ADMIN · DC · DDC · SC · ASC · PMA

## Features
- Document management (create, track, movements)
- Attachments & remarks per document
- Role-based access control
- Audit log with CSV export
- User management (admin only)

## Project Structure
├── rms-backend/   — Spring Boot REST API
└── rms-frontend/  — Vue 3 SPA

## Quick Start

### Backend
# Set environment variables
setx JWT_SECRET "your_base64_secret"
setx DB_USERNAME "root"
setx DB_PASSWORD "your_password"
setx APP_UPLOAD_DIR "C:/customs_uploads"

cd rms-backend
mvn spring-boot:run
# Runs on http://localhost:8080

### Frontend
cd rms-frontend
npm install
npm run dev
# Runs on http://localhost:5173

## Default Credentials (dev seed)
admin / Admin@123

## Backend config details: see rms-backend/README.md
