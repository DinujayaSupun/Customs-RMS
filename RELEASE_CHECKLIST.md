# Customs RMS Pre-Production Release Checklist

Use this checklist before each company release.

## 1) Staging Deployment (Must Pass)

1. Prepare a fresh staging database.
2. Set production-like environment variables:
   - `DB_USERNAME`
   - `DB_PASSWORD`
   - `JWT_SECRET`
   - `APP_UPLOAD_DIR`
3. Start backend and frontend with release configuration.
4. Verify backend health endpoint:
   - `GET /api/health` returns `200`.

## 2) Critical Smoke Flow (Must Pass)

1. Login as `admin`.
2. Create workflow user(s) as needed (DC/DDC/SC/PMA).
3. Login as workflow user and create a document.
4. Forward the document to another user.
5. Login as recipient and open the document from inbox.
6. Add a minute/remark.
7. Upload an attachment and download it.
8. Confirm audit log records for key actions.

## 3) Security and Permission Smoke (Must Pass)

1. Non-admin user cannot access:
   - `/users`
   - `/permissions`
   - `/logs`
2. Protected APIs reject missing/invalid token requests (401/403).
3. Query-string token works only on intended download/image GET routes.
4. Deactivated user cannot log in.

## 4) Data Safety (Must Pass)

1. Take full database backup before production release.
2. Verify restore procedure is tested (or recently proven).
3. Confirm rollback plan:
   - previous deployable backend/frontend artifacts
   - DB rollback/restore steps

## 5) Runtime and Ops Checks (Must Pass)

1. Upload directory exists and is writable.
2. Backend logs are written as expected.
3. Timezone/clock setup is correct on server and database.
4. No severe backend exceptions during smoke run.

## 6) Final Go/No-Go

Release only if all are true:

1. No blocker or critical defects.
2. Auth/permission/security smoke checks pass.
3. Core flow passes: login -> create -> forward -> download.
4. Rollback path is confirmed and ready.
