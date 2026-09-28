# Linguagem Ubíqua — FIAP X

> [!NOTE]
> **Por que este documento existe**
> A [documentação de arquitetura](../architecture/README.md) aplica os *conceitos* de DDD (bounded contexts, arquitetura hexagonal), mas os termos do domínio também precisavam estar fixados num artefato próprio — é o que a Fase 1 exige (linguagem ubíqua aplicada, entregável formal). Trato este glossário como a fonte única de nomenclatura para código, testes, eventos, interface e documentação. Um termo conceitual daqui não implica, por si só, uma classe, tabela ou fila própria.

## 📖 Termos do domínio

| Termo | Definição | Não confundir com |
|---|---|---|
| 🎬 **Vídeo** | O arquivo original enviado pelo usuário e seus metadados (nome, tamanho, dono). Entidade `Video`, tabela `videos` | O `.zip` de frames — esse é o **Resultado** |
| ⚙️ **Job de Processamento** | O trabalho de "extrair frames deste Vídeo". Modelado pelo próprio ciclo de vida do `Video` — não existe tabela `jobs` nem classe `Job` | A mensagem na fila: ela é o *transporte* do pedido, não o Job |
| 🚦 **Status** | Estado do Vídeo: `QUEUED`, `PROCESSING`, `COMPLETED` ou `FAILED` (definições abaixo) | "Progresso" percentual — o sistema só expõe esses 4 estados discretos |
| ⏳ `QUEUED` | Upload aceito e persistido, aguardando processamento; o pedido pode ainda estar na outbox | Vídeo "na fila SQS" — a publicação acontece depois, pelo job da outbox |
| 🔄 `PROCESSING` | A API recebeu `PROCESSING_STARTED` do worker | Prova instantânea de que o pod está vivo — isso é o heartbeat/lease |
| ✅ `COMPLETED` | Resultado aplicado pela API, com a chave do ZIP no S3 | ZIP gravado no S3 mas ainda não aplicado (janela entre worker e API) |
| ❌ `FAILED` | Falha definitiva de negócio, ou resultado após esgotar as tentativas; sempre acompanhada de `error_message` | Falha transitória (ex.: S3 indisponível por segundos) — absorvida pela reentrega, nunca vira `FAILED` sozinha |
| 📦 **Resultado** | O ZIP com os frames extraídos (1 frame/s, até 854 × 480), privado no S3 e baixado por streaming pela API | O Vídeo original — arquivos e chaves distintos |
| 👤 **Usuário** | Pessoa autenticada por e-mail e senha, dona de zero ou mais Vídeos; nunca vê Vídeos de outro Usuário (404) | "Cliente" (domínio da oficina mecânica das fases anteriores — não reaproveitar) |
| 🛡️ **Administrador** | Usuário com papel `ADMIN`, com endpoints próprios em `/admin` | Operação/equipe que recebe **Alertas operacionais** |
| 📤 **Outbox** | Eventos gravados na mesma transação da alteração de negócio e publicados depois no SQS | A fila em si — a outbox é o *buffer transacional* antes da fila |
| 🔒 **Lease** | Reserva temporária de trabalho com dono e prazo: na outbox (Postgres, 30 s) e no processamento (S3, 90 s) | Lock de banco — o lease expira sozinho se o dono morrer |
| 🔁 **Reentrega** | Nova entrega da mesma mensagem pelo SQS, após a visibilidade expirar; exige consumidores idempotentes | Retry dentro do código — aqui quem repete é a fila |
| 🪦 **DLQ** | Fila (sufixo `-dlq`) das mensagens que esgotaram 3 recebimentos | Descarte — a mensagem fica retida para investigação e replay |
| ✉️ **Notificação** | E-mail ao Usuário informando a falha do seu Vídeo | Resposta HTTP de erro (ex.: `400` no upload) — isso é validação de request |
| 🚨 **Alerta operacional** | Webhook opcional para a equipe quando o e-mail falha, sem dado pessoal; não encerra a retentativa do e-mail | Alerta do Alertmanager (saúde da plataforma) — outro destino e outro payload |
| 📬 `SENT` | Envio aceito pelo canal; para e-mail, o SMTP aceitou a mensagem | Chegada à caixa de entrada — comprovada à parte, pelo E2E via IMAP |

## 🟧 Eventos de domínio (vocabulário formal)

Nomeados no passado, como fatos já ocorridos — ver [Event Storming](./event-storming.md) para o quadro completo:

| Fato | Representação no sistema |
|---|---|
| Upload solicitado | `VideoUploadRequested` — na outbox e depois na fila `fiapx-video-processing` |
| Processamento iniciado | `PROCESSING_STARTED` — worker obteve o lease; fila `fiapx-video-status-updates` |
| Processamento concluído | `PROCESSING_COMPLETED` — ZIP gravado no S3; mesma fila de resultados |
| Processamento falhou | `PROCESSING_FAILED` — com motivo; mesma fila de resultados |
| Notificação solicitada | `NotificationRequested` — gravado pela API na outbox ao aplicar `FAILED`; fila `fiapx-video-notification` |
| Notificação enviada | Estado `SENT` em `NotificationAttempt` — não há evento `NotificationSent` publicado |

Cada fila tem uma DLQ com sufixo `-dlq`.

## Regra de nomenclatura

- Português nos documentos e na interface, inglês no código (entidades, eventos, filas): `Video`, `VideoUploadRequested`, `fiapx-video-processing`.
- Um termo da tabela = um nome no código. Se o código introduzir um sinônimo (ex.: chamar `Video` de `Media`), corrijo o código, não a tabela.
- Os valores de resultado seguem o enum `ProcessingEventType` (`PROCESSING_*`), que é o contrato da fila; o nome de negócio fica em português na coluna "Fato".

## Referências

- [Event Storming](./event-storming.md)
- [Domain Storytelling](./domain-storytelling.md)
- [Context Map](./context-map.md)
- [Documentação de arquitetura](../architecture/README.md)
