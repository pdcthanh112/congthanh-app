# Bucket S3 riêng cho một workload trong EKS + quyền qua EKS Pod Identity (không key tĩnh).
# Dùng cho checkpoint/savepoint Flink (streaming/flink) và backup Velero (velero/velero).
resource "aws_s3_bucket" "this" {
  bucket        = var.name
  force_destroy = var.force_destroy
  tags          = var.tags
}

resource "aws_s3_bucket_public_access_block" "this" {
  bucket                  = aws_s3_bucket.this.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "this" {
  bucket = aws_s3_bucket.this.id
  rule {
    apply_server_side_encryption_by_default { sse_algorithm = "aws:kms" }
    bucket_key_enabled = true
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "this" {
  count  = var.expire_days > 0 ? 1 : 0
  bucket = aws_s3_bucket.this.id
  rule {
    id     = "expire"
    status = "Enabled"
    filter {}
    expiration { days = var.expire_days }
    abort_incomplete_multipart_upload { days_after_initiation = 2 }
  }
}

data "aws_iam_policy_document" "trust" {
  statement {
    actions = ["sts:AssumeRole", "sts:TagSession"]
    principals {
      type        = "Service"
      identifiers = ["pods.eks.amazonaws.com"]
    }
  }
}

data "aws_iam_policy_document" "access" {
  statement {
    actions   = ["s3:ListBucket", "s3:GetBucketLocation", "s3:ListBucketMultipartUploads"]
    resources = [aws_s3_bucket.this.arn]
  }
  statement {
    actions   = ["s3:GetObject", "s3:PutObject", "s3:DeleteObject", "s3:AbortMultipartUpload", "s3:ListMultipartUploadParts"]
    resources = ["${aws_s3_bucket.this.arn}/*"]
  }
  dynamic "statement" {
    for_each = length(var.extra_actions) > 0 ? [1] : []
    content {
      actions   = var.extra_actions
      resources = ["*"]
    }
  }
}

resource "aws_iam_role" "this" {
  name               = "${var.name}-pod"
  assume_role_policy = data.aws_iam_policy_document.trust.json
  tags               = var.tags
}

resource "aws_iam_role_policy" "this" {
  name   = "bucket-access"
  role   = aws_iam_role.this.id
  policy = data.aws_iam_policy_document.access.json
}

resource "aws_eks_pod_identity_association" "this" {
  for_each        = toset(var.service_accounts)
  cluster_name    = var.cluster_name
  namespace       = var.namespace
  service_account = each.key
  role_arn        = aws_iam_role.this.arn
  tags            = var.tags
}
