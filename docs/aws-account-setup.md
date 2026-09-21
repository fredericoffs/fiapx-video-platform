# Configuração da conta AWS

Este projeto roda em uma conta AWS comum (não mais no Learner Lab). O Terraform cria as IAM roles do
cluster e dos nós (`k8s/terraform/aws/iam.tf`); o CI usa a chave de um usuário IAM.

## 1. Proteger a conta (uma vez, no console)

1. Entre como **root** e ative **MFA** (Security credentials → Assign MFA device).
2. Crie um usuário IAM `fiapx-deployer` (IAM → Users → Create user), sem acesso ao console.
3. Anexe a policy `AdministratorAccess`. O Terraform cria VPC, EKS, RDS, ElastiCache, SQS, SSM e
   IAM roles, então uma policy enxuta quebraria com facilidade. Como a chave não expira, guarde-a
   só nos secrets do GitHub e apague-a (ou desative-a) quando o projeto terminar.
4. Security credentials → Create access key → *Command Line Interface*. Guarde o par
   `Access key ID` / `Secret access key`; o segredo só aparece uma vez.
5. Pare de usar o root no dia a dia.

## 2. Orçamento (evitar susto na fatura)

Billing → Budgets → Create budget → *Monthly cost budget*, com alertas por e-mail em **US$ 5, 20 e 50**.

Esta stack **não cabe no free tier**: EKS cobra US$ 0,10/h de control plane, e há 2 nós `t3.large`,
um NLB, RDS e ElastiCache. Ligada, custa da ordem de US$ 0,30 a 0,40/h. Rode `Destroy AWS` ao fim de
cada sessão de testes e confirme com `scripts/aws-validate.sh --strict` que não sobrou nada.

## 3. AWS CLI local

```bash
aws configure          # Access key, Secret, região us-east-1, formato json
aws sts get-caller-identity
```

## 4. GitHub

No repositório: Settings → Environments → **AWS**. Grave os secrets com o script (cole o bloco
`[default] aws_access_key_id=… aws_secret_access_key=…`):

```bash
pbpaste | ./scripts/aws-sync-gh-secrets.sh --from-stdin --save-profile
```

Ou à mão: `AWS_ACCESS_KEY_ID` e `AWS_SECRET_ACCESS_KEY`. Não há mais `AWS_SESSION_TOKEN`.
Opcionais (`PROD_DB_PASSWORD`, `PROD_JWT_SECRET`, `PROD_ADMIN_PASSWORD`,
`PROD_NOTIFICATION_WEBHOOK_URL`, `PROD_SMTP_*`, `PROD_ALERTMANAGER_WEBHOOK_URL`) continuam como antes.

## 5. Primeira subida

1. Se o CI de `main` estiver verde, um push em `main` dispara `cd-aws.yml`. Ou rode-o manualmente.
2. A primeira execução leva ~25 min (EKS + RDS).
3. Verifique com `kubectl get pods -n fiapx` (todos `Running`) e abra o host do NLB no navegador (`kubectl get svc -n ingress-nginx`).

## 6. Limpeza

`Destroy AWS` (`destroy-aws.yml`) remove k8s, Terraform (inclusive as IAM roles) e faz a varredura
por `aws` CLI. O bucket de state (`fiapx-terraform-state-<account>`) só é apagado com a opção
explícita do script.

## Pontos de atenção

- **Cota/serviços**: contas novas podem ter limites de vCPU on-demand e restrições do plano
  gratuito. Se `apply` falhar com `VcpuLimitExceeded` ou serviço bloqueado, abra um pedido em
  Service Quotas ou mude para uma conta paga.
- **Acesso ao cluster**: quem cria o cluster vira admin dele. O CD faz Terraform e `kubectl` com
  a mesma chave, então funciona. Para outro usuário IAM usar `kubectl`, é preciso um *access entry*.
- **Região**: o padrão continua `us-east-1` (`AWS_REGION` nos workflows e `configmap.yaml`).
