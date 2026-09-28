# Ambiente AWS — Academy Learner Lab

O caminho de execução versionado usa o **AWS Academy Learner Lab**, com credenciais temporárias e roles pré-existentes. Não é um guia para uma conta pessoal com chaves permanentes. As restrições fornecidas pelo laboratório estão em [Learner_Lab.md](Learner_Lab.md); a configuração executável está no [Terraform](../k8s/terraform/aws/).

## 1. Preparar a sessão

1. Inicie a sessão do laboratório e confira o saldo disponível.
2. Em AWS Details → AWS CLI → Show, copie o bloco de credenciais temporárias.
3. Renove os secrets no Environment `AWS` do GitHub e, se necessário, o perfil local:

```bash
pbpaste | ./scripts/aws-sync-gh-secrets.sh --from-stdin --save-profile
aws sts get-caller-identity
```

São necessários `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` e **`AWS_SESSION_TOKEN`**. Todos expiram com a sessão. O script usa `aws` e `gh`; `--save-profile` atualiza o perfil local `default`.

## 2. Configurar a aplicação

Siga a [tabela de secrets e variáveis do README](../README.md#deploy-na-aws-eks). Configure SMTP e remetente reais, credenciais para a confirmação de e-mail do E2E e um receptor compatível com o payload do Alertmanager. O webhook operacional de falha de e-mail é opcional.

O Terraform gera ou recebe os segredos de banco, JWT e admin e os persiste no SSM. O deploy monta secrets Kubernetes separados por serviço. Não use a senha histórica de uma migração como credencial de acesso: o admin é inicializado pelo segredo do ambiente e exige troca de senha.

## 3. Implantar

O workflow `CD - AWS EKS` exige CI aprovado para o mesmo commit, inclusive quando iniciado manualmente. Ele provisiona recursos, publica cinco imagens no ECR, aplica migrações e deployments e executa o teste E2E. `provision: skip` pula somente o provisionamento.

Para operar localmente são necessários `aws`, `gh`, `terraform`, `kubectl`, `kustomize` e `helm`. Para testes Java de integração, Docker; para scripts de demonstração, consulte `--help` de cada script.

```bash
aws eks update-kubeconfig --region us-east-1 --name fiapx
kubectl get pods -n fiapx
kubectl get svc -n ingress-nginx
```

O endereço público muda quando o ambiente é recriado. O certificado HTTPS é autoassinado; os scripts de teste aceitam explicitamente esse certificado. O frontend local também depende de backend implantado e CORS compatível com sua origem.

## 4. Validar e registrar

O E2E roda no fim de cada deploy; a demonstração de concorrência e o teste de resiliência estão descritos no [README](../README.md#teste-de-ponta-a-ponta-no-ambiente-implantado) e em [Riscos e resiliência](architecture/05-riscos-e-resiliencia.md#2-como-a-resiliência-é-verificada). Associe cada resultado ao commit implantado. O teste de resiliência derruba pods e altera temporariamente a política SQS: execute em uma janela dedicada.

## 5. Encerrar a sessão

Execute o workflow `Destroy AWS` e depois verifique a limpeza:

```bash
./scripts/aws-validate.sh --strict
```

A destruição remove recursos e dados do ambiente. Exporte evidências e arquivos necessários antes. A varredura complementar reduz resíduos, mas falhas de permissão ou credenciais expiradas ainda podem impedir a limpeza. O bucket de state possui tratamento próprio no script; consulte suas opções antes de removê-lo.

## Limitações assumidas

- Roles do laboratório (`LabRole` e role de cluster descoberta pelo Terraform), sem criação de IAM roles próprias.
- Identidade AWS via role do nó; não há IRSA por serviço nesta topologia.
- RDS compartilhado por schemas, ainda com credencial comum.
- Sem execução completa local e sem promessa de disponibilidade fora da sessão.
- Custo depende dos recursos e do tempo ligados; confira o orçamento do laboratório, sem tratar estimativas antigas como cotação atual.
