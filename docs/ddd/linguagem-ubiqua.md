# Linguagem Ubíqua — Sistema de Processamento de Vídeos (FIAP X)

> [!NOTE]
> **Por que este documento existe**
> A [documentação de arquitetura](../architecture/hld-lld-adr-rfc.md) já aplica os *conceitos* de DDD (bounded contexts, arquitetura hexagonal), mas nunca fixou os termos do domínio como artefato — exatamente o que a Fase 1 exige (linguagem ubíqua aplicada, entregável formal). Este glossário é a fonte única de nomenclatura para código, testes, eventos e documentação — se um termo não está aqui, não deveria aparecer em `camelCase` no código sem antes ser adicionado aqui.

## Termos do domínio

| Termo | Definição | Não confundir com |
|---|---|---|
| **Vídeo** | O arquivo binário original enviado pelo usuário, mais seus metadados (nome, formato, tamanho). Representado pela entidade `Video`. | O `.zip` de frames gerado — esse é o **Resultado**, não o Vídeo |
| **Job de Processamento** | A unidade de trabalho que representa "extrair frames deste Vídeo". Um Vídeo tem no máximo um Job ativo por vez. Em código, hoje modelado como o próprio agregado `Video` com campo `status` — ver nota abaixo | Uma mensagem na fila (a mensagem é o *transporte* do comando de processar, não o Job em si) |
| **Status** | Estado do ciclo de vida de um Vídeo: `QUEUED` (na fila, aguardando worker), `PROCESSING` (worker está extraindo frames), `COMPLETED` (zip gerado com sucesso), `FAILED` (falhou após esgotar tentativas) | "Progresso" (percentual) — o sistema não expõe progresso granular, só esses 4 estados discretos |
| **Resultado** | O arquivo `.zip` contendo os frames extraídos (1 frame/segundo via `ffmpeg -vf fps=1`), armazenado no MinIO/S3 e disponibilizado por URL pré-assinada | O Vídeo original — arquivos distintos, `storage_key` diferentes |
| **Usuário** | Pessoa autenticada por e-mail/senha, dona de zero ou mais Vídeos. Nunca vê Vídeos de outro Usuário | "Cliente" (termo usado no domínio da oficina mecânica das fases anteriores — não reaproveitar aqui) |
| **Falha de Processamento** | Erro definitivo (após esgotar retries) durante a extração de frames — vídeo corrompido, formato não suportado, timeout. Sempre acompanhada de `error_message` e dispara notificação | Falha transitória (ex.: MinIO indisponível por 1s) — essa é absorvida por retry, nunca vira `FAILED` sozinha |
| **Notificação** | Comunicação assíncrona ao Usuário informando uma Falha de Processamento (e-mail, com webhook como fallback — ver [ADR-011](../architecture/hld-lld-adr-rfc.md#adr-011--notificação-multicanal-como-incremento-não-como-núcleo)) | Resposta HTTP síncrona de erro (ex.: `400` no upload) — isso não é "Notificação", é validação de request |
| **Fila de Processamento** | A fila de mensageria (`video.processing`) que desacopla o recebimento do Vídeo da sua extração de frames — o mecanismo central que garante RF1/RF2 do enunciado | O `outbox_events` do Postgres — este é o *buffer transacional* antes da fila, não a fila em si |

## Eventos de domínio (vocabulário formal)

Nomeados no passado, como fatos já ocorridos — ver [Event Storming](./event-storming.md) para o quadro completo:

- `VideoUploadRequested` — usuário concluiu o upload; vídeo persistido e enfileirado.
- `ProcessingStarted` — worker pegou o job da fila e começou a extrair frames.
- `ProcessingCompleted` — frames extraídos, zip gerado e armazenado com sucesso.
- `ProcessingFailed` — processamento falhou definitivamente após esgotar retries.
- `NotificationSent` — notificação de falha foi entregue ao usuário (por algum canal).

## Regra de nomenclatura

- Português nos documentos/UI, inglês no código (entidades, eventos, nomes de fila) — mesmo padrão já usado nos ADRs existentes (`Video`, `VideoUploadRequested`, fila `video.processing`).
- Um termo da tabela acima = um nome de classe/tabela/evento no código. Se o código introduzir um sinônimo (ex.: chamar `Job` de `Task` em algum lugar), corrigir o código, não a tabela.

## Nota aberta

O termo "Job de Processamento" ainda não tem uma classe própria no LLD — hoje é só o campo `status` do agregado `Video`. Isso é uma simplificação aceitável para o escopo do hackathon (ver [Event Storming](./event-storming.md), seção Agregados), mas fica registrado aqui para não gerar confusão de nomenclatura entre este glossário e o código.
