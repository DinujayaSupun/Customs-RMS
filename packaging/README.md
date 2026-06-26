# Customs RMS — Offline Docker Bundle

This bundle runs the whole application (database + backend + frontend) in Docker
**without needing the internet or any build step**. Everything is pre-built.

## What's inside

| File | What it is |
|------|------------|
| `images.tar` | The three pre-built Docker images (backend, frontend, MySQL). |
| `docker-compose.offline.yml` | Tells Docker how to run the three containers together. |
| `run.ps1` / `run.sh` | One-shot launcher (loads images, then starts everything). |
| `README.md` | This file. |

## Requirements on the target machine

- **Docker** installed and running. That's the only requirement.
  - Windows/Mac: Docker Desktop. Linux: Docker Engine + the `docker compose` plugin.
- Ports **3000**, **8080**, and **3307** free.

## Run it

**Windows:**
```powershell
powershell -ExecutionPolicy Bypass -File run.ps1
```

**Linux/macOS:**
```bash
chmod +x run.sh && ./run.sh
```

**Or do it manually (any OS):**
```bash
docker load -i images.tar
docker compose -f docker-compose.offline.yml up -d
```

Then open **http://localhost:3000** and log in:

| Username | Password | Role |
|----------|----------|------|
| `admin`  | `admin123` | Administrator |
| `dc`, `ddc`, `sc`, `asc`, `pma` | `password123` | Workflow users |

## Everyday commands

```bash
docker compose -f docker-compose.offline.yml ps        # status
docker compose -f docker-compose.offline.yml logs -f    # live logs
docker compose -f docker-compose.offline.yml down       # stop (keeps data)
docker compose -f docker-compose.offline.yml down -v    # stop AND erase all data
docker compose -f docker-compose.offline.yml up -d       # start again
```

Your data (database + uploaded files) persists in Docker volumes across stop/start.
Only `down -v` erases it.

## Important notes

- **Accessing from another computer.** The frontend is pre-built to talk to the API at
  `http://localhost:8080`, so it works when you browse from the **same machine** that runs
  Docker. To serve real users over a network, the frontend must be rebuilt with
  `VITE_API_BASE_URL` set to the server's real address, behind an Nginx reverse proxy with
  HTTPS. See the project's `DEPLOYMENT.md` for the production setup.
- **This bundle uses demo credentials** (`admin123`, etc.) and seeds demo users — it is meant
  for evaluation/demo, not a public production server. For production, follow `DEPLOYMENT.md`
  (fresh `JWT_SECRET`, real passwords, `prod` profile, TLS).
