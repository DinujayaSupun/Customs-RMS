# Quickstart — Run Customs RMS with Docker

This is the fastest way to get the whole application (database + backend + frontend)
running on one machine. For the full production deployment (TLS, Nginx, `prod` profile),
see [DEPLOYMENT.md](DEPLOYMENT.md) — Appendix A covers Docker specifically.

## Requirements

- **Docker** installed and running (Docker Desktop on Windows/Mac, Docker Engine + the
  `docker compose` plugin on Linux) — and an internet connection the first time, so Docker
  can download the base images and build.
- Ports **3000**, **8080**, and **3307** free.

> No internet on the target machine? Use the **offline image bundle** instead — it ships
> pre-built images and needs no build step. See Appendix A of DEPLOYMENT.md.

## Run it

From the project root:

```bash
docker compose up --build -d
```

The first run takes a few minutes (it downloads images, then compiles the backend and
frontend). When it finishes, open:

**http://localhost:3000**

Log in with:

| Username | Password | Role |
|----------|----------|------|
| `admin`  | `admin123` | Administrator |
| `dc`, `ddc`, `sc`, `asc`, `pma` | `password123` | Workflow users |

## Everyday commands

```bash
docker compose ps             # see what's running
docker compose logs -f         # live logs (add a service name, e.g. backend)
docker compose down            # stop everything (keeps your data)
docker compose down -v         # stop AND erase the database + uploads
docker compose up -d           # start again (no rebuild)
docker compose up --build -d   # rebuild after code changes, then start
```

Your data (database + uploaded files) lives in Docker volumes and survives `down`/`up`.
Only `down -v` erases it.

## Good to know

- The frontend is built to call the API at `http://localhost:8080`, so it works when you
  browse from the **same machine** running Docker. To serve other users over a network, the
  frontend must be rebuilt with `VITE_API_BASE_URL` set to the server's real address and put
  behind an HTTPS reverse proxy — see [DEPLOYMENT.md](DEPLOYMENT.md).
- This setup uses **demo credentials and seeded users** for convenience. It is for
  evaluation/development, **not** a public production server. For production, follow
  DEPLOYMENT.md (fresh `JWT_SECRET`, real passwords, `prod` profile, TLS).

## Other Docker setups

- **Production-style stack** — `docker-compose.prod.yml` (+ `.env.prod.example`) runs the `prod`
  profile with external secrets and bind-mounted data. See DEPLOYMENT.md Appendix A.6.
- **Offline / air-gapped delivery** — `make-bundles.ps1` produces a self-contained zip that runs
  from pre-built images with no internet. See `packaging/README.md` and DEPLOYMENT.md Appendix A.
