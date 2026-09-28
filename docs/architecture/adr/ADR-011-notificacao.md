# ADR-011 — Notificação: e-mail ao usuário e alerta operacional

[← Índice de ADRs](README.md) · [Arquitetura](../README.md)

| Campo | Valor |
|---|---|
| Status | Aceito |
| Tema | Aviso de falha |

## Contexto

O requisito pede avisar o usuário quando o processamento falha. Um canal de notificação pode ficar lento ou indisponível sem afetar o pipeline principal. Um webhook genérico só prova que um endpoint respondeu, não que o dono do vídeo foi avisado — e enviar o e-mail do usuário a um endpoint de terceiros é exposição de dado pessoal (LGPD).

## Decisão

- **E-mail é o único canal de entrega ao usuário.** O deploy exige SMTP real.
- **Webhook é alerta operacional, opcional:** avisa a equipe de que o e-mail falhou, com `videoId` e motivo, **sem o e-mail do usuário**; o operador encontra o dono em `/admin/videos`.
- **O sucesso do webhook não encerra a notificação:** com o e-mail falhando, o serviço alerta a operação (uma vez por vídeo) e relança a falha, e a reentrega do SQS continua tentando o e-mail até a DLQ.
- Cada canal tem Circuit Breaker, Bulkhead e prazo por tentativa, para que um destino lento não prenda o consumidor.
- **A chegada é comprovada:** o E2E de cada deploy encontra o e-mail na caixa de entrada por IMAP.

## Alternativas consideradas

- **Webhook como canal equivalente ao e-mail** — rejeitado: um 2xx não prova que o usuário foi avisado, e exigiria enviar dado pessoal.
- **Receptor que reencaminha o webhook ao usuário (ex.: Lambda + SES)** — rejeitado: mais infraestrutura no laboratório para resolver o que o SMTP já resolve.
- **Multicanal desde o início** — rejeitado como ponto de partida: o núcleo do pipeline veio primeiro.

## Consequências

- **Positivas:** falha de notificação nunca bloqueia o processamento; o usuário é avisado de fato; sem dado pessoal fora do sistema.
- **Negativas:** depende de um SMTP real (Gmail com App Password na demonstração); esgotadas as tentativas, a mensagem fica na DLQ e exige correção do canal e replay.

## Evidência

E2E em cada deploy: arquivo inválido → `FAILED` → notificação pelo canal EMAIL → mensagem encontrada na INBOX do destinatário (27/09/2026: o e-mail chegou cerca de 1 s depois do `FAILED`).

## Histórico de revisões

- **Prazo por tentativa de canal** — Circuit Breaker e Bulkhead limitam a taxa, não a duração: um destino que aceita conexão e nunca responde prendia o consumidor. Foram definidos timeouts de SMTP e HTTP e um prazo final por tentativa.
- **27/09/2026 · webhook sem dado pessoal** — o webhook recebia o e-mail do usuário e contava como entrega. Passou a ser alerta operacional sem PII, e o deploy passou a exigir SMTP real.
- **27/09/2026 · retentativa do e-mail** — com o SMTP falhando e o webhook respondendo, a mensagem era concluída e o e-mail nunca era tentado de novo. O serviço passou a relançar a falha mesmo após o alerta.

[← Índice de ADRs](README.md) · [Arquitetura](../README.md)
