# FIAP X — Interface web

[![CI](https://github.com/fredericoffs/fiapx-video-platform/actions/workflows/ci.yml/badge.svg?branch=develop)](https://github.com/fredericoffs/fiapx-video-platform/actions/workflows/ci.yml)
[![Qodana](https://github.com/fredericoffs/fiapx-video-platform/actions/workflows/qodana.yml/badge.svg?branch=develop)](https://github.com/fredericoffs/fiapx-video-platform/actions/workflows/qodana.yml)
![Cobertura](https://img.shields.io/badge/cobertura%20Qodana-82%25-yellowgreen)
![React](https://img.shields.io/badge/React-19-61DAFB?logo=react&logoColor=black)
![TypeScript](https://img.shields.io/badge/TypeScript-strict-3178C6?logo=typescript&logoColor=white)
![Vite](https://img.shields.io/badge/Vite-8-646CFF?logo=vite&logoColor=white)
![Vitest](https://img.shields.io/badge/Vitest-unit%20%2B%20coverage-6E9F18?logo=vitest&logoColor=white)
![Playwright](https://img.shields.io/badge/Playwright-e2e-2EAD33?logo=playwright&logoColor=white)

SPA em React, TypeScript e Vite para cadastro, login, upload, consulta de status, download de ZIP e administração. O frontend consome o `video-gateway`; o backend completo roda no EKS.

## Desenvolvimento

Use Node 22. Dentro de `web/`:

```bash
cp .env.example .env
npm ci
npm run dev
```

Configure `VITE_API_BASE_URL` para um gateway acessível. O gateway deve permitir a origem do Vite em `GATEWAY_CORS_ALLOWED_ORIGINS`; a configuração padrão do deploy permite a origem pública, não automaticamente `localhost`. HTTPS com certificado autoassinado também exige confiança no navegador.

## Verificação

| Comando                 | Finalidade                               |
| ----------------------- | ---------------------------------------- |
| `npm run typecheck`     | Verificar tipos                          |
| `npm run lint`          | Verificar limites de módulos e qualidade |
| `npm test`              | Testes unitários e de componentes        |
| `npm run test:coverage` | Relatório de cobertura da execução       |
| `npm run build`         | Tipos e bundle de produção               |
| `npm run format:check`  | Conferir formatação                      |
| `npm run test:e2e`      | Jornada pelo navegador com backend real  |

A cobertura deve ser consultada no relatório do CI; não há percentual fixo documentado como resultado atual.

Para testar o ambiente implantado:

```bash
npx playwright install chromium
E2E_BASE_URL=https://<host-do-nlb> npm run test:e2e
```

Com `E2E_BASE_URL`, Playwright não sobe o Vite e aceita o certificado autoassinado. Sem essa variável, inicia o frontend local e usa o backend configurado. O teste de navegador e o smoke de API do deploy são verificações diferentes; o CI atual não executa Playwright.

## Contrato OpenAPI

`npm run codegen` consulta **`http://localhost:8081/v3/api-docs`**, conforme `package.json`; não lê `VITE_API_BASE_URL`. Para usar o serviço implantado, abra um túnel em outro terminal:

```bash
kubectl -n fiapx port-forward service/video-api 8081:8081
```

Depois execute `npm run codegen` e revise `src/shared/api/schema.gen.ts`. O CI gera o contrato a partir dos testes da API e compara os tipos gerados com o arquivo versionado.

## Configuração do container

O Dockerfile compila com Node e serve o bundle com nginx não-root na porta 8080. O entrypoint gera `env-config.js`; o cliente resolve a URL nesta ordem:

1. `window.__ENV__.API_BASE_URL`, definida pela variável **`API_BASE_URL` no container**.
2. `VITE_API_BASE_URL`, definida no build, como fallback.
3. `http://localhost:8080`, fallback final.

O deploy injeta a URL pública em runtime. Portanto, mudar o endereço do gateway não exige reconstruir a imagem web.

## Organização

```text
src/
  app/       # providers, layout e rotas
  features/  # funcionalidades agrupadas por domínio de interface
  shared/    # contrato da API, componentes e utilitários compartilhados
```

[Projeto e execução](../README.md) · [Arquitetura](../docs/architecture/README.md)
