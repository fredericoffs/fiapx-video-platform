# ADR-009 — API Gateway: Spring Cloud Gateway em vez de Kong

[← Índice de ADRs](README.md) · [Arquitetura](../README.md)

| Campo | Valor |
|---|---|
| Status | Aceito |
| Tema | Borda HTTP |

## Contexto

O cliente precisa de um endereço único na frente do `video-api`, com CORS e limite de requisições de borda. Kong e Spring Cloud Gateway eram as opções; a comparação detalhada está no [RFC-002](../rfc/RFC-002-spring-cloud-gateway-vs-kong.md).

## Decisão

Spring Cloud Gateway (variante servlet) como `video-gateway`:

- Roteia `/auth`, `/videos`, `/admin` e `/users` para o `video-api`.
- Gera ou reaproveita o `X-Correlation-Id`, devolve-o na resposta e registra escritas e erros.
- Aplica rate limit com contador de janela fixa no Redis: 20 requisições por 60 s por **IP do cliente**, família de rota e método (downloads separados).
- **Não** valida JWT: a API valida, o que a mantém testável sem o gateway.

O IP do cliente chega intacto porque o Service do ingress usa `externalTrafficPolicy=Local`.

## Alternativas consideradas

- **Kong DB-less** — rejeitado: o plugin JWT é modelado em torno de *consumers* cadastrados, ruim para usuários que se registram sozinhos; adiciona infraestrutura fora da stack Java.
- **Traefik** — viável como proxy, sem ganho, já que o JWT não é validado na borda.

## Consequências

- **Positivas:** stack única em Java/Spring; roteamento declarativo; testes com o mesmo ferramental dos demais serviços.
- **Negativas:** menos genérico que um gateway de mercado; a cota por IP pode penalizar usuários atrás do mesmo NAT.

## Evidência

Resiliência `burst` em 27/09/2026: 25 uploads simultâneos de um mesmo IP resultaram em 20 aceitos e 5 recusados com 429, e só os aceitos foram persistidos.

## Histórico de revisões

- **27/09/2026 · IP do cliente** — com `externalTrafficPolicy=Cluster`, o kube-proxy trocava a origem pelo IP do nó, e o gateway contava por nó: todos os clientes dividiam duas cotas (observado: 25 uploads e nenhum 429). O ingress passou a usar `externalTrafficPolicy=Local`, e o teste passou a reprovar quando a cota não é aplicada.

[← Índice de ADRs](README.md) · [Arquitetura](../README.md)
