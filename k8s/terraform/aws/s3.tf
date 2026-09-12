# Buckets de vídeo (originais e zips): fiapx-videos-{raw,processed}-<account>. Criados por
# scripts/aws-buckets-init.sh (aws CLI, idempotente, antes do plan) e não por aws_s3_bucket:
# a SCP do Learner Lab nega s3:GetBucketObjectLockConfiguration, que o provider chama ao ler
# o bucket, e o apply falha logo após a criação. O Terraform só publica os nomes (outputs).
locals {
  video_buckets = {
    raw       = "${var.project}-videos-raw-${local.account_id}"
    processed = "${var.project}-videos-processed-${local.account_id}"
  }
}

# Esquece do state os buckets criados por aws_s3_bucket numa versão anterior deste módulo,
# sem apagá-los (os objetos e a limpeza ficam com scripts/aws-destroy.sh).
removed {
  from = aws_s3_bucket.videos

  lifecycle {
    destroy = false
  }
}
