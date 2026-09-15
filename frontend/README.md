# Lumen AI Code Reviewer frontend

Vite + React + TypeScript + Tailwind dashboard for the AI Code Review Council backend.

## Quick start
```bash
# 1. install deps
npm install

# 2. start backend (in repo root, separate terminal)
cd ..
java -jar target/lumen.jar serve --server.port=8090

# 3. start dev server
npm run dev
# → http://localhost:5789
```

## Pages
- **Dashboard** — health check, quick actions, session lookup
- **Validate** — paste `council.yaml`, run schema check
- **Graph** — rendered Mermaid of the live LangGraph4j StateGraph
- **Session detail** — `/sessions/{id}` shows cost, tokens, raw session JSON

## Build
```bash
npm run build      # → dist/
npm run preview    # serve dist/ locally
```

## Proxy
Vite dev server proxies `/api/*` → `http://localhost:8090` (see `vite.config.ts`).
In production, point a static server (nginx etc.) at `dist/` and proxy `/api` to the backend.
