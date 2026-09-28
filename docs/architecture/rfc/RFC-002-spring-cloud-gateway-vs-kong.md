# RFC-002 — API Gateway: Spring Cloud Gateway em vez de Kong

| Campo  | Valor                                                                                                                    |
|--------|--------------------------------------------------------------------------------------------------------------------------|
| Status | Aceito — decisão registrada no [ADR-009](../adr/ADR-009-api-gateway.md) |
| Autor  | Frederico Ferreira                                                                                                       |
| Escopo | Serviço `video-gateway`, entrada HTTP da API, atrás do ingress                                                          |

## Problema

O frontend e os clientes de API precisam de um endereço único na frente de `video-api` (autenticação, upload, status, download, admin), com CORS e um
limite de requisições de borda. O HLD original deixou "Kong ou Spring Cloud Gateway" em aberto.

## Proposta

Usar **Spring Cloud Gateway** (variante servlet, `spring-cloud-starter-gateway-server-webmvc`) como o serviço `video-gateway`:

- Roteamento declarativo: `/auth/**`, `/videos/**`, `/admin/**` e `/users/**` para `video-api`.
- CORS configurado por variável de ambiente (`GATEWAY_CORS_ALLOWED_ORIGINS`), preenchida em runtime com a URL pública do cluster.
- Rate limiting de borda com contador de janela fixa no Redis (`INCR` por IP, 20 requisições a cada 60 s por chave de IP/família de rota/método, separando downloads, por padrão), complementar ao limite de
  tentativas de login que já existe no `video-api`.
- Correlation-id gerado na borda e propagado por header até os workers.
- O JWT **não** é validado no gateway. O `video-api` valida o token com Spring Security, então continua testável isolado, sem
  depender do gateway.

## Alternativa considerada: Kong

| Critério         | Spring Cloud Gateway                                                    | Kong (DB-less)                                                                                            |
|------------------|-------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------|
| Stack            | Java/Spring, mesmo ferramental de teste dos outros serviços             | Lua/NGINX, configuração declarativa em YAML próprio                                                       |
| Validação de JWT | Feita na API; gateway só roteia                                   | Plugin JWT modelado em *Consumers* cadastrados; usuários auto-registrados exigiriam sincronizar consumers |
| Rate limiting    | Filtro próprio com Redis, duas classes pequenas com teste de integração | Plugin pronto, mas configuração fora do repositório de código                                             |
| Testes           | `@SpringBootTest` com MockMvc, mesmo `mvn verify` e gate de cobertura   | Testes de integração contra o container do Kong                                                           |
| Peso operacional | Um container JVM com recursos definidos no Deployment                                               | Um container NGINX, mais leve, porém mais um sistema para conhecer                                        |

Traefik também foi avaliado como proxy puro. Sem validação de JWT na borda, ele não traz ganho sobre o Spring Cloud Gateway e sai do stack Java.

## Consequências

- Positivas: quatro serviços na mesma linguagem e mesmo ciclo de build/teste/cobertura; roteamento, CORS e rate limiting versionados junto do código;
  o gateway é um serviço hexagonal como os outros, com testes de integração que já pegaram um bug real (rota `/admin/**` ausente).
- Negativas: menos genérico que um gateway de mercado; quem avalia conhecimento de Kong/Traefik especificamente não vê essa ferramenta aqui. A troca
  futura é localizada, porque nenhum serviço depende do gateway para segurança.

## Como validar

Fluxo completo pela URL pública do `ingress-nginx` no cluster EKS: registro, login, upload, status, download e área admin respondem igual à chamada
direta ao `video-api`. Rate limiting: exceder a cota da mesma chave (IP, família de rota, método e grupo de download) retorna `429`.
