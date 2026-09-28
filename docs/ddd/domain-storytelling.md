# Domain Storytelling — Jornada do Usuário

> [!NOTE]
> **Formato**
> Narro aqui a jornada em linguagem de negócio, na ordem em que acontece, usando os termos da [Linguagem Ubíqua](./linguagem-ubiqua.md). Este documento complementa o [Event Storming](./event-storming.md), que organizei por evento e agregado, não por narrativa cronológica.

## História 1 — 🎬 Caminho feliz: do upload ao download

![Domain Storytelling caminho feliz.svg](../architecture/img/Domain%20Storytelling%20caminho%20feliz.svg)

**Narrativa:** *O Usuário se autentica no Sistema e envia um Vídeo. O Sistema confirma o recebimento assim que o arquivo está guardado, sem fazer o Usuário esperar o processamento. Em paralelo, o Sistema entrega o Vídeo a um Worker de Processamento, que avisa quando começa, extrai os frames e informa quando o Resultado está pronto. A qualquer momento o Usuário pode consultar o status dos seus Vídeos — só dos seus — e, quando um deles estiver pronto, baixar o Resultado.*

## História 2 — ❌ Caminho de falha: o Usuário é avisado

![Domais storytelling falha.svg](../architecture/img/Domais%20storytelling%20falha.svg)

**Narrativa:** *Quando o Worker não consegue processar um Vídeo, ele informa o Sistema da Falha de Processamento e do motivo. O Sistema marca o Vídeo como falho e, no mesmo passo, registra que o Usuário precisa ser avisado — assim não existe falha sem aviso nem aviso sem falha. O Serviço de Notificação manda o e-mail. Se o provedor de e-mail estiver fora, a equipe é avisada, mas isso não conta como aviso ao Usuário: o e-mail continua sendo tentado. Diferente do caminho feliz, aqui o Usuário é avisado ativamente, sem precisar ficar consultando o status.*

## O que esta narrativa deixa claro (e o Event Storming, sozinho, não)

- ⏱️ O Usuário **nunca espera** o processamento dentro da mesma interação: a confirmação do upload chega antes de o Worker começar. Essa é a diferença central em relação à aplicação de referência, que rodava o `ffmpeg` dentro da requisição.
- 📣 A Notificação é **iniciativa do Sistema**, não resposta a uma pergunta do Usuário — por isso é um fluxo assíncrono desacoplado, e não mais um `GET`.
- 🧭 Quem decide notificar é o **Sistema (video-api)**, depois de registrar a falha — nunca o Worker diretamente. O Worker só relata o que aconteceu.
- 🔐 Falhas transitórias (rede, S3, fila) **não** viram falha para o Usuário: a mensagem é reentregue e o trabalho continua.

## Referências

- [Event Storming](./event-storming.md)
- [Linguagem Ubíqua](./linguagem-ubiqua.md)
- [Context Map](./context-map.md)
