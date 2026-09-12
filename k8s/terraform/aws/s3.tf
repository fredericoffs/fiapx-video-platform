# Buckets privados de vídeos (originais e zips). Nome com o account id (S3 é global) e
# force_destroy porque o ambiente é descartado a cada sessão.
resource "aws_s3_bucket" "videos" {
  for_each = toset(["raw", "processed"])

  bucket        = "${var.project}-videos-${each.key}-${local.account_id}"
  force_destroy = true

  tags = {
    Name = "${var.project}-videos-${each.key}"
  }
}

resource "aws_s3_bucket_public_access_block" "videos" {
  for_each = aws_s3_bucket.videos

  bucket                  = each.value.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "videos" {
  for_each = aws_s3_bucket.videos

  bucket = each.value.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_versioning" "videos" {
  for_each = aws_s3_bucket.videos

  bucket = each.value.id

  versioning_configuration {
    status = "Disabled"
  }
}
