# ADR-004 — Armazenamento de vídeo e ZIP processado

[← Índice de ADRs](README.md) · [Arquitetura](../README.md)

| Campo | Valor |
|---|---|
| Status | Aceito |
| Tema | S3 privado para binários |

## Contexto

Vídeos e ZIPs são binários grandes (até 500 MB de entrada; o ZIP pode ser maior). O banco relacional deve guardar metadados, não arquivos.

## Decisão

Amazon S3 com dois buckets privados (originais e resultados), acessados pelo AWS SDK v2 com a cadeia padrão de credenciais (role do nó). O nome original do arquivo é apenas metadado: a chave interna usa o UUID do vídeo (`raw/{id}/source.{ext}`, `processed/{id}/{id}.zip`), e o nome nunca vira caminho em disco nem chave de storage. O download do ZIP é feito por streaming pela API, depois da verificação de propriedade.

## Alternativas consideradas

- **`bytea` no PostgreSQL** — rejeitado: degrada backup, replicação e WAL.
- **Disco local do pod** — rejeitado: não sobrevive a várias réplicas nem ao reagendamento de pods (é o problema da aplicação de referência).
- **URL pré-assinada** — não adotada por ora: exporia o objeto a quem tiver o link durante a validade; o streaming mantém a autorização na API.

## Consequências

- **Positivas:** buckets sem exposição pública; nenhuma chave estática; exclusões e uploads interrompidos são limpos de forma persistente (`storage_cleanup`).
- **Negativas:** o download passa pela API, consumindo rede do `video-api`; retenção de arquivos de negócio ainda não tem política de ciclo de vida.

## Histórico de revisões

- **Download em streaming no frontend** — o navegador acumulava o ZIP inteiro em memória antes de salvar; onde a File System Access API existe, o download passou a gravar direto no disco conforme os bytes chegam.
- **Exclusão bloqueada fora de estado terminal** — excluir um vídeo em `QUEUED`/`PROCESSING` podia deixar um ZIP órfão gravado depois pelo worker; a exclusão passou a exigir estado terminal (HTTP 409 antes disso).

[← Índice de ADRs](README.md) · [Arquitetura](../README.md)
