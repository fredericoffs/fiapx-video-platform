# Documentação — FIAP X

Comece pelo [README principal](../README.md) para entender o produto e executar o projeto. Esta documentação descreve a implementação versionada e separa configuração de evidência operacional.

| Objetivo | Documento |
|---|---|
| Conferir requisitos e entregáveis | [Enunciado original](enunciado.md) |
| Ver a arquitetura por assunto | [Índice da arquitetura](architecture/README.md) |
| Ligar requisitos à implementação e às evidências | [Requisitos e evidências](architecture/01-requisitos.md) |
| Entender o problema e a proposta | [RFC](architecture/02-rfc.md) |
| Entender componentes, fluxos, implantação e observabilidade | [HLD](architecture/03-hld.md) |
| Entender dados, contratos e mecanismos internos | [LLD](architecture/04-lld.md) |
| Avaliar riscos e o resultado dos testes de falha | [Riscos e resiliência](architecture/05-riscos-e-resiliencia.md) |
| Entender o porquê de cada decisão | [14 ADRs](architecture/adr/README.md) |
| Preparar o ambiente do laboratório | [Guia AWS](aws-account-setup.md) |
| Consultar o material fornecido pelo lab | [AWS Academy Learner Lab](Learner_Lab.md) |
| Entender a escolha do gateway | [RFC-002](architecture/rfc/RFC-002-spring-cloud-gateway-vs-kong.md) |
| Explorar a API | [Collection Postman](postman/fiapx-video-api.postman_collection.json) |
| Desenvolver o frontend | [README web](../web/README.md) |

## Domínio

- [Linguagem ubíqua](ddd/linguagem-ubiqua.md): termos e contratos.
- [Context Map](ddd/context-map.md): responsabilidades e fronteiras.
- [Event Storming](ddd/event-storming.md): comandos, fatos e políticas.
- [Domain Storytelling](ddd/domain-storytelling.md): jornadas de sucesso e falha.

## Fontes executáveis

- [Migrações da API](../video-api/src/main/resources/db/migration/) e [notificações](../notification-worker/src/main/resources/db/migration/).
- [Manifests Kubernetes](../k8s/apps/base/), [Terraform](../k8s/terraform/aws/) e [workflows](../.github/workflows/).
- [Dashboards Grafana](../k8s/addons/dashboards/) e [scripts operacionais](../scripts/).

Os diagramas Mermaid ficam no próprio Markdown, evitando imagens externas desatualizadas. Os documentos de origem (enunciado e material do laboratório) são preservados como referência.

Projeto desenvolvido no Hackathon da Fase 5 — PosTech FIAP.
