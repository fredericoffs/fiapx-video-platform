# ADR-012 — AWS EKS como única topologia de execução

[← Índice de ADRs](README.md) · [Arquitetura](../README.md)

| Campo | Valor |
|---|---|
| Status | Aceito |
| Tema | Execução no AWS Academy Learner Lab |

## Contexto

O requisito de escalabilidade pede prova de execução real. O ambiente disponível é o AWS Academy Learner Lab: credenciais temporárias, roles pré-criadas (não é possível criar IAM roles), orçamento limitado e recursos recriados a cada sessão ([restrições](../../Learner_Lab.md)).

## Decisão

Toda a stack roda em AWS EKS, provisionado por Terraform, sem caminho de execução local paralelo.

- **Cluster:** EKS 1.36 (versão em suporte padrão), 3 nós `t3.large`, roles do laboratório (`LabRole` nos nós; role de cluster localizada por padrão de nome), IMDS com hop limit 2 para os pods lerem a credencial do nó.
- **Rede:** VPC sem NAT Gateway, sub-redes em 2 AZs, um NLB na frente do ingress-nginx com TLS autoassinado (cert-manager).
- **Entrega:** `cd-aws.yml` espera o CI aprovado **do mesmo commit** (inclusive em execução manual), aplica o Terraform só quando há diferença, publica 5 imagens no ECR, faz o deploy e termina com o E2E.
- **Imagens:** tags do ECR ficam **mutáveis**. O CD publica `latest` junto com o SHA do commit, e reexecutar o CD no mesmo commit (comum depois de renovar as credenciais do laboratório) gera outra imagem com a mesma tag de SHA; com tags imutáveis, os dois casos quebram o deploy. O deploy referencia só a tag do SHA, então o risco de uma tag mudar por baixo de um pod em execução é baixo. Achado `AWS-0031` do Trivy de IaC aceito conscientemente.
- **Segredos:** as únicas credenciais no GitHub são as da sessão do laboratório e as de SMTP e Alertmanager; senha do banco, segredo JWT e senha do admin ficam no SSM.
- **Custo:** o ambiente sobe para validar ou gravar e é destruído em seguida (`destroy-aws.yml`), com varredura por nome independente do state e validação de resíduo zero.

## Alternativas consideradas

- **Execução local (Docker Compose/kind) como padrão, nuvem opcional** — foi o desenho inicial; removido depois da validação na AWS para não manter dois conjuntos de manifests e scripts.
- **Serverless (Lambda, Step Functions, DynamoDB)** — rejeitado: runtime e autenticação já assumem containers ([ADR-005](ADR-005-autenticacao.md), [ADR-007](ADR-007-runtime.md)).

## Consequências

- **Positivas:** um único caminho de deploy para manter e documentar; escala demonstrada com HPA e KEDA reais.
- **Negativas:** exige uma sessão ativa do laboratório e credenciais renovadas; sem IRSA, todos os pods usam a role do nó; o endereço público muda a cada recriação.

## Evidência

Ambiente provisionado, validado e destruído várias vezes em 27/09/2026; o destroy terminou com `aws-validate.sh --strict` sem resíduos.

## Histórico de revisões

- **CD condicionado ao CI** — CI e CD disparavam em paralelo, e um commit reprovado podia ser implantado. O CD passou a esperar o CI do mesmo SHA.
- **Checagem de migrações** — uma migração nova sem alteração em `k8s/` não acionava a verificação de que ela está no ConfigMap do Job de migração; o filtro de caminhos passou a incluir as migrações.
- **27/09/2026** — o deploy passou a terminar com um E2E real (upload, ZIP, falha e e-mail na caixa de entrada), e um workflow manual de resiliência foi adicionado; ambos compartilham o grupo de concorrência `fiapx-aws-lifecycle` com o deploy e o destroy.
- **29/09/2026** — o scan de IaC (`security-scan.yml`) apontou tags mutáveis no ECR (`AWS-0031`); a decisão de mantê-las foi registrada em *Imagens*, acima. No mesmo scan, as filas SQS passaram a declarar SSE-SQS explicitamente.
- **29/09/2026** — o node group passou de 2 para 3 nós `t3.large`: sem Cluster Autoscaler, o KEDA pedia o 3º `video-worker` e ele ficava `Pending` (`Insufficient cpu`, ~220–320m livres por nó contra 500m de request). Os workers também ganharam `topologySpreadConstraints` por nó.

[← Índice de ADRs](README.md) · [Arquitetura](../README.md)
