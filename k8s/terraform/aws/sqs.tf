# 3 filas Standard + 3 DLQs (redrive após max_receive_count). Nomes batem com os defaults do
# perfil aws dos serviços (application-aws.yml). Visibilidade da fila de processamento cobre o
# prazo do ffmpeg; o consumer ainda estende com heartbeat enquanto trabalha.
locals {
  queues = {
    processing = {
      name               = "${var.project}-video-processing"
      visibility_timeout = var.sqs_processing_visibility_timeout_seconds
    }
    status_updates = {
      name               = "${var.project}-video-status-updates"
      visibility_timeout = 60
    }
    notification = {
      name               = "${var.project}-video-notification"
      visibility_timeout = 60
    }
  }
}

resource "aws_sqs_queue" "dlq" {
  for_each = local.queues

  name                      = "${each.value.name}-dlq"
  message_retention_seconds = var.sqs_message_retention_seconds

  tags = {
    Name = "${each.value.name}-dlq"
  }
}

resource "aws_sqs_queue" "main" {
  for_each = local.queues

  name                       = each.value.name
  visibility_timeout_seconds = each.value.visibility_timeout
  message_retention_seconds  = var.sqs_message_retention_seconds
  receive_wait_time_seconds  = 20

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.dlq[each.key].arn
    maxReceiveCount     = var.sqs_max_receive_count
  })

  tags = {
    Name = each.value.name
  }
}

# A DLQ só aceita redrive vindo da fila principal correspondente.
resource "aws_sqs_queue_redrive_allow_policy" "dlq" {
  for_each = local.queues

  queue_url = aws_sqs_queue.dlq[each.key].id
  redrive_allow_policy = jsonencode({
    redrivePermission = "byQueue"
    sourceQueueArns   = [aws_sqs_queue.main[each.key].arn]
  })
}
