# Event Storming — FIAP X

> [!NOTE]
> **Formato**
> Fiz este documento como equivalente textual/Mermaid a um board de Event Storming (Miro), conforme aceito pela Fase 1 ("Miro ou equivalente"). Ele mapeia o fluxo **implementado**: quando o nome de um fato de negócio difere do valor que trafega na fila, os dois aparecem (ex.: *Processamento concluído* = `PROCESSING_COMPLETED`).
>
> **Convenção de cores do método:**
> - 🟧 Evento de domínio
> - 🟦 Comando
> - 🟨 Ator/Persona
> - 🟪 Agregado
> - 🟩 Read Model
> - 🟥 Política (regra reativa)
> - ⬛ Sistema externo
> - ❗ Hotspot (ponto de incerteza)

## 1. Linha do tempo — fluxo feliz e fluxo de falha

![event storming.svg](../architecture/img/event%20storming.svg)

## 2. Atores

| Ator | O que faz |
|---|---|
| 🟨 **Usuário** | Cadastra-se, autentica, envia Vídeo, consulta status, baixa o Resultado, exclui Vídeo terminado, troca a senha |
| 🟨 **Administrador** | Lista usuários e todos os vídeos, exclui usuários e vídeos alheios; o primeiro acesso exige trocar a senha semeada |
| ⚙️ **Sistema** | Publica a outbox, processa o vídeo, aplica o resultado, envia o e-mail e limpa objetos órfãos no S3 |

## 3. Comandos e quem os dispara

| Comando | Disparado por | Pré-condição |
|---|---|---|
| 🟦 `CadastrarUsuario` | Usuário (`POST /auth/register`) | E-mail ainda não cadastrado |
| 🟦 `Autenticar` | Usuário (`POST /auth/login`) | Menos de 5 falhas para o e-mail nos últimos 60 s |
| 🟦 `EnviarVideo` | Usuário (`POST /videos`) | Autenticado, sem troca de senha pendente; arquivo de até 500 MB |
| 🟦 `IniciarProcessamento` | `video-worker` (fila `fiapx-video-processing`) | Mensagem `VideoUploadRequested` e lease do vídeo livre no S3 |
| 🟦 `AplicarResultado` | `video-api` (fila `fiapx-video-status-updates`) | Vídeo ainda não terminal (resultado repetido é ignorado) |
| 🟦 `NotificarUsuario` | `notification-worker` (fila `fiapx-video-notification`) | Reivindicação (vídeo, canal) obtida; nenhum envio `SENT` anterior |
| 🟦 `ExcluirVideo` | Dono ou administrador (`DELETE /videos/{id}`) | Vídeo em `COMPLETED` ou `FAILED` (409 antes disso) |
| 🟦 `ExcluirUsuario` | Administrador (`DELETE /admin/users/{id}`) | Nenhum vídeo do usuário em `QUEUED`/`PROCESSING` |
| 🟦 `TrocarSenha` | Usuário (`PUT /users/me/password`) | Senha atual correta; revoga os tokens anteriores |

## 4. Eventos de domínio (definição formal em [Linguagem Ubíqua](./linguagem-ubiqua.md))

🟧 `VideoUploadRequested` → 🟧 `PROCESSING_STARTED` → (🟧 `PROCESSING_COMPLETED` **ou** 🟧 `PROCESSING_FAILED`) → 🟧 `NotificationRequested` → 🟧 notificação `SENT` (só no caminho de falha)

A notificação enviada é um fato registrado como `NotificationAttempt.status = SENT`, e não um evento publicado numa quarta fila. `SENT` significa que o provedor SMTP aceitou a mensagem; a chegada à caixa de entrada é comprovada à parte, pelo E2E via IMAP.

## 5. Agregados

| Agregado | Invariantes que protege |
|---|---|
| 🟪 **User** | E-mail único; senha só como hash BCrypt; papel `USER` ou `ADMIN`; `tokens_valid_after` revoga tokens emitidos antes de uma troca de senha |
| 🟪 **Video** | Um único `status` por vez; estado terminal (`COMPLETED`/`FAILED`) não regride; pertence a exatamente um usuário; concorrência protegida por optimistic locking (`version`) |
| 🟪 **NotificationAttempt** | Um envio por (vídeo, canal): índice único parcial sobre `SENDING`/`SENT` impede duas execuções simultâneas; `SENT` nunca é reenviado |

> [!WARNING]
> **Decisões registradas**
> - Não existe agregado `Job` separado de `Video`: o "job" é o próprio ciclo de vida do vídeo. Não há reprocessamento paralelo do mesmo vídeo nem histórico de tentativas além do que o SQS e a DLQ já registram.
> - O `video-worker` **não** tem um segundo agregado `Video` em banco: é stateless e só mantém o lease e o ZIP no S3 ([ADR-008](../architecture/adr/ADR-008-status-por-evento.md)).
> - `QUEUED → COMPLETED/FAILED` direto é permitido: o SQS Standard não garante ordem, e um `PROCESSING_STARTED` atrasado não pode regredir o estado.

## 6. Políticas (regras reativas — "quando X, então Y")

| Política | Gatilho | Ação |
|---|---|---|
| 🟥 Enfileirar após upload | Vídeo e `VideoUploadRequested` gravados na mesma transação | Job da outbox publica em `fiapx-video-processing` (a cada 3 s) |
| 🟥 Avisar início | Worker obteve o lease do vídeo | Publica `PROCESSING_STARTED` em `fiapx-video-status-updates` |
| 🟥 Aplicar conclusão | `PROCESSING_COMPLETED` recebido pela API | Grava `COMPLETED` e a chave do ZIP no schema `video_api` — o worker nunca escreve ali ([ADR-008](../architecture/adr/ADR-008-status-por-evento.md)) |
| 🟥 Falha de negócio | Arquivo inválido, duração acima de 1.800 s ou erro de extração | Publica `PROCESSING_FAILED` sem repetir uma extração sabidamente inválida |
| 🟥 Aplicar falha e notificar | `PROCESSING_FAILED` recebido pela API | Grava `FAILED` + `NotificationRequested` na outbox, na mesma transação |
| 🟥 Falha transitória | S3, SQS ou rede indisponível no worker | Não apaga a mensagem; o SQS reentrega após a visibilidade; o heartbeat protege o trabalho em andamento |
| 🟥 Esgotou tentativas | Mensagem recebida 3 vezes sem sucesso | Vai para a DLQ (`-dlq`); a DLQ de processamento leva o vídeo a estado terminal, recuperando `COMPLETED` se o ZIP já existir |
| 🟥 E-mail falhou | Erro de SMTP, circuito aberto ou prazo excedido | Alerta a operação por webhook (uma vez por vídeo, sem o e-mail do usuário) e relança a falha: a reentrega tenta o e-mail de novo |
| 🟥 Limpeza de órfãos | Reserva de upload vencida ou exclusão pendente | Job da API apaga o objeto no S3 (a cada 10 s) |

## 7. Read Models

| Read Model | Endpoint | Fonte |
|---|---|---|
| 🟩 Listagem de status do usuário | `GET /videos` | Postgres do `video-api`, paginada, só os vídeos do dono, sem cache ([ADR-013](../architecture/adr/ADR-013-sem-cqrs.md)) |
| 🟩 Detalhe de um vídeo | `GET /videos/{id}` | Postgres do `video-api`; vídeo alheio responde 404 |
| 🟩 Download do resultado | `GET /videos/{id}/download` | Streaming do ZIP pelo `video-api` (bucket S3 privado, sem URL pré-assinada), só em `COMPLETED` |
| 🟩 Visão administrativa | `GET /admin/users`, `GET /admin/videos` | Postgres do `video-api`, papel `ADMIN` |

## 8. Hotspots (pontos de incerteza)

- ❗ **Pico sustentado acima da capacidade:** a borda recusa com 429 acima de 20 requisições/min por IP e o KEDA escala até 3 workers; acima disso a fila cresce. Não há controle de admissão pela profundidade da fila — o alerta `FiapxWorkerCapacityExhausted` avisa a operação.
- ❗ **`SENT` não é "recebido":** o aceite do SMTP não garante a chegada; só o E2E confirma a caixa de entrada.
- ❗ **DLQ exige gente:** mensagens que esgotaram as tentativas disparam `FiapxDeadLetterQueueNotEmpty` e precisam de investigação e replay controlado.

## Referências

- [Linguagem Ubíqua](./linguagem-ubiqua.md)
- [Domain Storytelling](./domain-storytelling.md)
- [Context Map](./context-map.md)
- [Documentação de arquitetura](../architecture/README.md)
