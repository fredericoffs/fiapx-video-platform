# Domain Storytelling — Jornada do Usuário

> [!NOTE]
> **Formato**
> Narro aqui a jornada em linguagem de negócio, na ordem em que acontece, usando os substantivos/verbos da [Linguagem Ubíqua](./linguagem-ubiqua.md) — este documento complementa o [Event Storming](./event-storming.md) (que organizei por evento/agregado, não por narrativa cronológica única).

## História 1 — Caminho feliz

```mermaid
sequenceDiagram
    autonumber
    actor Usuario as Usuário
    participant Sistema as Sistema (FIAP X)
    participant Worker as Worker de Processamento

    Usuario->>Sistema: 1. Autentica-se (e-mail + senha)
    Sistema-->>Usuario: 2. Confirma identidade (token)
    Usuario->>Sistema: 3. Envia um Vídeo
    Sistema-->>Usuario: 4. Confirma recebimento (Vídeo em QUEUED)
    Sistema->>Worker: 5. Entrega o Vídeo para processamento
    Worker->>Worker: 6. Extrai frames do Vídeo
    Worker->>Sistema: 7. Informa que o Resultado está pronto
    Usuario->>Sistema: 8. Consulta o status dos seus Vídeos
    Sistema-->>Usuario: 9. Mostra status COMPLETED
    Usuario->>Sistema: 10. Baixa o Resultado
    Sistema-->>Usuario: 11. Entrega o arquivo .zip
```

**Narrativa:** *O Usuário se autentica no Sistema e envia um Vídeo. O Sistema confirma o recebimento imediatamente, sem fazer o Usuário esperar o processamento. Em paralelo, o Sistema entrega o Vídeo para um Worker de Processamento, que extrai os frames. Quando o Worker termina, ele informa o Sistema, que atualiza o status do Vídeo. O Usuário, a qualquer momento, pode consultar o status de todos os seus Vídeos e, quando um deles estiver pronto, baixar o Resultado.*

## História 2 — Caminho de falha

```mermaid
sequenceDiagram
    autonumber
    actor Usuario as Usuário
    participant Sistema as Sistema (FIAP X)
    participant Worker as Worker de Processamento
    participant Notif as Serviço de Notificação

    Usuario->>Sistema: 1. Envia um Vídeo
    Sistema-->>Usuario: 2. Confirma recebimento (QUEUED)
    Sistema->>Worker: 3. Entrega o Vídeo para processamento
    Worker->>Worker: 4. Tenta extrair frames — falha (ex.: arquivo corrompido)
    Worker->>Worker: 5. Tenta novamente algumas vezes (retry)
    Worker->>Sistema: 6. Informa Falha de Processamento definitiva
    Sistema->>Notif: 7. Pede para notificar o Usuário
    Notif->>Usuario: 8. Envia Notificação (e-mail)
    Usuario->>Sistema: 9. Consulta status
    Sistema-->>Usuario: 10. Mostra status FAILED + motivo do erro
```

**Narrativa:** *Quando o Worker não consegue extrair os frames de um Vídeo mesmo após tentar novamente, ele informa o Sistema de uma Falha de Processamento definitiva. O Sistema então aciona o Serviço de Notificação, que avisa o Usuário por e-mail. Diferente do caminho feliz, aqui o Usuário é avisado ativamente — não precisa ficar consultando o status para descobrir que algo deu errado.*

## O que esta narrativa deixa claro (e o Event Storming, sozinho, não)

- O Usuário **nunca espera** o processamento terminar dentro da mesma interação (passo 4 da História 1 acontece "em paralelo", não bloqueia o passo 4→resposta). Essa é a diferença central entre o baseline antigo (síncrono) e o sistema novo.
- A notificação (História 2, passo 7-8) é **iniciativa do Sistema**, não uma resposta a uma pergunta do Usuário — por isso é um fluxo assíncrono desacoplado, não mais um `GET` que o Usuário faria.

## Referências

- [Event Storming](./event-storming.md)
- [Linguagem Ubíqua](./linguagem-ubiqua.md)
- [Context Map](./context-map.md)
