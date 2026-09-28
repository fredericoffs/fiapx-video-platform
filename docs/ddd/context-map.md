# Context Map — FIAP X

> [!NOTE]
> **Relação com a decomposição em serviços**
> Fiz os 3 serviços de negócio (`video-api`, `video-worker`, `notification-worker`) cobrirem **4 bounded contexts**: o `video-api` hospeda dois contextos (Identidade e Ingestão de Vídeo) no mesmo processo, por decisão pragmática de hackathon ([ADR-001](../architecture/adr/ADR-001-estilo-arquitetural.md)), não porque sejam o mesmo contexto de domínio. O `video-gateway` é infraestrutura de entrada, não um contexto de domínio adicional.

![Context Map.svg](../architecture/img/Context%20Map.svg)

Setas contínuas são integrações no mesmo processo ou com sistemas externos; tracejadas, mensagens assíncronas em fila.

## Relações entre contextos (padrões estratégicos de DDD)

| De → Para | Padrão | Por quê |
|---|---|---|
| 🔐 Identidade → 🎬 Ingestão | **Conformist** (Ingestão aceita o token que Identidade emite, sem traduzir) | Os dois convivem no `video-api`: não há fronteira de rede, mas a fronteira *conceitual* existe. O usuário é identificado pelo `sub` do JWT; a API também valida revogação (`tokens_valid_after`) e papel |
| 🎬 Ingestão → ⚙️ Processamento | **Customer/Supplier** via **Published Language** (evento SQS, não chamada direta) | Ingestão é upstream e define o contrato `VideoUploadRequested`. Processamento consome esse contrato e nunca lê o Postgres da API |
| ⚙️ Processamento → 🎬 Ingestão | **Published Language** (`PROCESSING_STARTED`, `PROCESSING_COMPLETED`, `PROCESSING_FAILED`) | Inverte a direção: Processamento publica, Ingestão aplica ao **próprio** estado ([ADR-008](../architecture/adr/ADR-008-status-por-evento.md)). Nenhum lado acessa o banco do outro |
| 🎬 Ingestão → ✉️ Notificação | **Published Language** (`NotificationRequested`, com o e-mail do destinatário) | A API grava o pedido na outbox **na mesma transação** em que aplica `FAILED`. A Notificação não conhece Vídeo, Job ou Usuário além do payload |

> [!IMPORTANT]
> **Limite essencial:** o worker **não** pede a notificação diretamente. Quem faz isso é a API, depois de aplicar a falha — assim o estado do vídeo e o pedido de notificação nunca divergem (não existe `FAILED` sem aviso, nem aviso de um vídeo que não falhou).

## Por que 4 contextos convivendo em só 3 serviços

Um domínio com contextos genuinamente distintos e fluxo transacional multi-etapa (ex.: orçamento → aprovação → execução → pagamento) justificaria um serviço por contexto, com Saga para compensação — não é o caso aqui. Os 4 contextos existem (a fronteira de domínio é real e está mapeada acima), mas fiz **2 deles conviverem no mesmo processo** (`video-api`) porque a comunicação entre Identidade e Ingestão é síncrona, local e não precisa escalar de forma independente — ao contrário do Processamento, a única parte CPU-bound, que escala sozinha pelo KEDA. A divisão conceitual não implica módulos físicos separados: Identidade e Ingestão compartilham as mesmas camadas hexagonais do serviço. É a decisão do [ADR-001](../architecture/adr/ADR-001-estilo-arquitetural.md), vista pela lente de bounded contexts em vez de "quantos serviços".

## Referências

- [Event Storming](./event-storming.md)
- [Linguagem Ubíqua](./linguagem-ubiqua.md)
- [Domain Storytelling](./domain-storytelling.md)
- [Documentação de arquitetura](../architecture/README.md)
