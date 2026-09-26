# IAM do cluster: role do control plane e role dos nós, criadas aqui (conta AWS normal —
# no Learner Lab elas vinham pré-criadas e eram achadas por regex).
#
# Sem IRSA/Pod Identity: os pods (video-api, video-worker, notification-worker), o KEDA e o
# driver EBS CSI herdam as permissões da role do nó via IMDS (hop limit 2, ver eks.tf).
# Por isso a role do nó carrega, além das políticas gerenciadas de worker, o acesso mínimo
# do projeto a S3 e SQS — restrito aos buckets e filas deste módulo, não a "*".

data "aws_iam_policy_document" "eks_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["eks.amazonaws.com"]
    }
  }
}

data "aws_iam_policy_document" "node_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "cluster" {
  name               = "${var.cluster_name}-eks-cluster"
  assume_role_policy = data.aws_iam_policy_document.eks_assume.json
}

resource "aws_iam_role_policy_attachment" "cluster" {
  role       = aws_iam_role.cluster.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSClusterPolicy"
}

resource "aws_iam_role" "node" {
  name               = "${var.cluster_name}-eks-node"
  assume_role_policy = data.aws_iam_policy_document.node_assume.json
}

resource "aws_iam_role_policy_attachment" "node" {
  for_each = {
    AmazonEKSWorkerNodePolicy          = "arn:aws:iam::aws:policy/AmazonEKSWorkerNodePolicy"
    AmazonEKS_CNI_Policy               = "arn:aws:iam::aws:policy/AmazonEKS_CNI_Policy"
    AmazonEC2ContainerRegistryReadOnly = "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"
    # Única das quatro sob o path service-role/, não a raiz de policy/.
    AmazonEBSCSIDriverPolicy = "arn:aws:iam::aws:policy/service-role/AmazonEBSCSIDriverPolicy"
  }

  role       = aws_iam_role.node.name
  policy_arn = each.value
}

data "aws_iam_policy_document" "node_app" {
  statement {
    sid       = "VideoBucketsObjects"
    actions   = ["s3:GetObject", "s3:PutObject", "s3:DeleteObject", "s3:AbortMultipartUpload", "s3:ListMultipartUploadParts"]
    resources = [for name in values(local.video_buckets) : "arn:aws:s3:::${name}/*"]
  }

  statement {
    sid       = "VideoBucketsList"
    actions   = ["s3:ListBucket", "s3:GetBucketLocation", "s3:ListBucketMultipartUploads"]
    resources = [for name in values(local.video_buckets) : "arn:aws:s3:::${name}"]
  }

  # GetQueueAttributes também serve ao KEDA (aws-sqs-queue trigger) e ao gauge de profundidade.
  statement {
    sid = "VideoQueues"
    actions = [
      "sqs:SendMessage",
      "sqs:ReceiveMessage",
      "sqs:DeleteMessage",
      "sqs:ChangeMessageVisibility",
      "sqs:GetQueueAttributes",
      "sqs:GetQueueUrl",
    ]
    resources = [for q in aws_sqs_queue.main : q.arn]
  }
}

resource "aws_iam_role_policy" "node_app" {
  name   = "${var.project}-app-access"
  role   = aws_iam_role.node.id
  policy = data.aws_iam_policy_document.node_app.json
}
