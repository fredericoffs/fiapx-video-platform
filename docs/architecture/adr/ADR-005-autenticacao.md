# ADR-005 — Autenticação

[← Índice de ADRs](README.md) · [Arquitetura](../README.md)

| Campo | Valor |
|---|---|
| Status | Aceito |
| Tema | Autenticação na API |

## Contexto

O enunciado pede acesso protegido por usuário e senha, sem exigir serverless nem provedor de identidade. Tokens precisam poder ser invalidados (troca de senha, remoção de usuário).

## Decisão

Autenticação dentro do `video-api`: senhas com BCrypt e JWT HS256 com segredo único, guardado no SSM e entregue só ao `video-api`. O token carrega o usuário em `sub`, o papel e a exigência de troca de senha. A revogação é por data (`tokens_valid_after`): tokens emitidos antes dela são recusados. O admin inicial recebe a senha pelo SSM no primeiro boot e precisa trocá-la.

O gateway não valida JWT: ele roteia e aplica limites; a autorização fica na API, que continua testável isoladamente ([ADR-009](ADR-009-api-gateway.md)).

## Alternativas consideradas

- **Lambda de autenticação** — rejeitada: nenhum requisito pede serverless, e adicionaria um runtime a mais.
- **Cognito/OAuth2** — rejeitado: esforço desproporcional ao escopo.

## Consequências

- **Positivas:** uma única fonte do segredo evita divergência entre réplicas; revogação efetiva sem lista de tokens.
- **Negativas:** cada requisição autenticada consulta o usuário no banco (necessário para a revogação); as virtual threads tornam esse custo aceitável sem cache.

## Histórico de revisões

- **Revogação e recuperação de sessão** — JWT puramente stateless não permitia invalidar um token (um admin removido continuaria operando). A migração V10 criou `tokens_valid_after`; a troca de senha reemite o token na resposta; o frontend passou a encerrar a sessão ao receber 401 fora do login.

[← Índice de ADRs](README.md) · [Arquitetura](../README.md)
