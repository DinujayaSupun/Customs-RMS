# Customs RMS — Frontend

Vue 3 single-page application for [Customs RMS](../README.md). Built with Vue 3 (`<script setup>`),
Vite, Vue Router, and axios; styled with Tailwind CSS; icons via lucide.

## Develop

```bash
npm install
npm run dev          # http://localhost:5173 (proxies /api to the backend on :8080)
```

The API base URL comes from `VITE_API_BASE_URL` (defaults to `http://localhost:8080`). It is baked
in at build time, so set it before `npm run build` for any non-local environment.

## Scripts

| Script | Purpose |
|--------|---------|
| `npm run dev` | Start the Vite dev server |
| `npm run build` | Production build to `dist/` |
| `npm run preview` | Preview the production build |
| `npm run test:unit` | Vitest unit tests |
| `npm run test:e2e` | Playwright end-to-end tests (needs a running backend) |

## Layout

```text
src/
|-- api/          # axios wrappers per backend area (auth, documents, logs)
|-- auth/         # session + current-user helpers
|-- components/   # shared UI components
|-- composables/  # reusable composition functions (toasts, debounced watch)
|-- layouts/      # app shell
|-- pages/        # route-level screens (Inbox, Documents, Details, Users, ...)
|-- router/       # Vue Router config + auth guards
|-- services/     # backend status + real-time WebSocket notifications
`-- utils/        # pure logic modules (unit-tested independently of components)
```

Most business logic lives in pure `utils/*.js` modules with matching `*.test.js` files, kept
separate from `.vue` components so it can be unit-tested directly.

For full project setup, environment variables, and deployment, see the
[root README](../README.md) and [DEPLOYMENT.md](../DEPLOYMENT.md).
