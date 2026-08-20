# fiapx video platform — web

SPA (React 19 + TypeScript + Vite) que consome exclusivamente o `video-gateway`.

## Setup

```bash
cp .env.example .env   # ajuste VITE_API_BASE_URL se necessário
npm install
npm run dev
```

## Scripts

- `npm run dev` — servidor de desenvolvimento (Vite).
- `npm run build` — typecheck (`tsc -b`) + build de produção.
- `npm run lint` — ESLint (`typescript-eslint` strict, `eslint-plugin-boundaries`).
- `npm run typecheck` — só o typecheck.
- `npm run format` / `format:check` — Prettier.
- `npm run codegen` — regenera `src/shared/api/schema.gen.ts` a partir do OpenAPI real do
  `video-api`. **Precisa do `video-api` de pé em `http://localhost:8081`**:
  ```bash
  cd .. && docker compose --profile app up -d postgres rabbitmq redis minio minio-init video-api
  cd web && npm run codegen
  ```
  O CI falha se o schema commitado divergir do gerado (`git diff --exit-code` depois de rodar
  o codegen contra o serviço real) — sempre rode `npm run codegen` e commite o resultado depois
  de qualquer mudança de contrato no `video-api`.

## Estrutura

Organização feature-based (reforçada por `eslint-plugin-boundaries` — sem import cruzado entre
`features/*` a não ser via `shared/`):

```
src/
  app/            # bootstrap: providers, layout raiz, rotas
  features/
    auth/         # login, registro
    upload/       # tela e lógica de upload
    videos/       # listagem de status, download
  shared/
    api/          # cliente gerado (OpenAPI) + wrapper openapi-fetch
    ui/           # componentes shadcn/ui reutilizáveis
    hooks/
    lib/          # session store (Zustand), utils
```
