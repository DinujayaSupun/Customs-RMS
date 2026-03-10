# RMS Backend Configuration Guide

This backend is configured to keep secrets out of Git.

## What Is Committed
- `src/main/resources/application.properties` contains safe defaults only.
- `src/main/resources/application-local.example.properties` is a template.

## What Is NOT Committed
- `src/main/resources/application-local.properties` (ignored by `.gitignore`).
- Real credentials and JWT secrets.

## Required Secret
- `JWT_SECRET` is mandatory.
- If `JWT_SECRET` is missing, backend startup will fail.

## Multi-Machine Setup
Use one of these approaches.

1. Shared backend server (recommended)
- Set `JWT_SECRET` once on the backend server.
- All frontend clients connect to this single backend.

2. Multiple backend machines in same environment
- Set the same `JWT_SECRET` on every backend machine in that environment.
- Tokens from one instance will then be valid on others.

## Windows Commands
Set environment variables permanently:

```powershell
setx JWT_SECRET "PUT_BASE64_SECRET_HERE"
setx DB_USERNAME "root"
setx DB_PASSWORD "your_password"
setx APP_UPLOAD_DIR "C:/customs_uploads"
```

After `setx`, open a new terminal and run:

```powershell
mvn spring-boot:run
```

## Optional Local Profile File
Create `src/main/resources/application-local.properties` from the example and run:

```powershell
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

## Generate a Base64 JWT Secret (PowerShell)

```powershell
[Convert]::ToBase64String((1..64 | ForEach-Object { Get-Random -Minimum 0 -Maximum 256 } | ForEach-Object { [byte]$_ }))
```
