# AWS Backup: sao lưu tập trung theo lịch cho dữ liệu quan trọng (Aurora; thêm tài nguyên bằng ARN hoặc tag).
# Aurora vẫn có backup tự động 7 ngày của RDS; AWS Backup thêm bản ngày/tháng giữ lâu hơn, mã hoá bằng KMS riêng,
# khoá vault chống xoá (ransomware, nhầm tay) và sao chép sang region khác.
# Cluster Kubernetes (manifest + PVC) do Velero sao lưu (module workload-bucket "velero").

data "aws_caller_identity" "this" {}

resource "aws_kms_key" "backup" {
  description             = "${var.name} AWS Backup vault"
  enable_key_rotation     = true
  deletion_window_in_days = 30
  tags                    = var.tags
}

resource "aws_kms_alias" "backup" {
  name          = "alias/${var.name}-backup"
  target_key_id = aws_kms_key.backup.key_id
}

resource "aws_backup_vault" "this" {
  name        = var.name
  kms_key_arn = aws_kms_key.backup.arn
  tags        = var.tags
}

resource "aws_backup_vault_lock_configuration" "this" {
  count              = var.vault_lock ? 1 : 0
  backup_vault_name  = aws_backup_vault.this.name
  min_retention_days = var.min_retention_days
  max_retention_days = max(var.daily_retention_days, var.monthly_retention_days)
  # Không đặt changeable_for_days → governance mode (gỡ được bằng quyền đặc biệt), không phải compliance
  # mode (không ai gỡ được, kể cả AWS). Chỉ chuyển sang compliance khi đã chắc chắn.
}

# Vault ở region khác cho bản sao.
resource "aws_kms_key" "copy" {
  count                   = var.copy_to_region != "" ? 1 : 0
  provider                = aws.copy
  description             = "${var.name} AWS Backup vault (bản sao)"
  enable_key_rotation     = true
  deletion_window_in_days = 30
  tags                    = var.tags
}

resource "aws_backup_vault" "copy" {
  count       = var.copy_to_region != "" ? 1 : 0
  provider    = aws.copy
  name        = "${var.name}-copy"
  kms_key_arn = aws_kms_key.copy[0].arn
  tags        = var.tags
}

resource "aws_backup_plan" "this" {
  name = var.name

  rule {
    rule_name         = "daily"
    target_vault_name = aws_backup_vault.this.name
    schedule          = "cron(0 18 * * ? *)" # 18:00 UTC = 1:00 sáng giờ Việt Nam, ngoài giờ cao điểm ở Mỹ
    start_window      = 60
    completion_window = 360
    lifecycle { delete_after = var.daily_retention_days }
    dynamic "copy_action" {
      for_each = var.copy_to_region != "" ? [1] : []
      content {
        destination_vault_arn = aws_backup_vault.copy[0].arn
        lifecycle { delete_after = var.daily_retention_days }
      }
    }
  }

  dynamic "rule" {
    for_each = var.monthly_retention_days > 0 ? [1] : []
    content {
      rule_name         = "monthly"
      target_vault_name = aws_backup_vault.this.name
      schedule          = "cron(0 19 1 * ? *)"
      start_window      = 60
      completion_window = 720
      lifecycle {
        cold_storage_after = 30
        delete_after       = max(var.monthly_retention_days, 120) # cold storage phải giữ tối thiểu 90 ngày
      }
      dynamic "copy_action" {
        for_each = var.copy_to_region != "" ? [1] : []
        content {
          destination_vault_arn = aws_backup_vault.copy[0].arn
          lifecycle {
            cold_storage_after = 30
            delete_after       = max(var.monthly_retention_days, 120)
          }
        }
      }
    }
  }
  tags = var.tags
}

data "aws_iam_policy_document" "assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["backup.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "backup" {
  name               = "${var.name}-backup"
  assume_role_policy = data.aws_iam_policy_document.assume.json
  tags               = var.tags
}

resource "aws_iam_role_policy_attachment" "backup" {
  for_each = toset([
    "arn:aws:iam::aws:policy/service-role/AWSBackupServiceRolePolicyForBackup",
    "arn:aws:iam::aws:policy/service-role/AWSBackupServiceRolePolicyForRestores",
  ])
  role       = aws_iam_role.backup.name
  policy_arn = each.value
}

resource "aws_backup_selection" "this" {
  name         = var.name
  plan_id      = aws_backup_plan.this.id
  iam_role_arn = aws_iam_role.backup.arn
  resources    = var.resource_arns
  selection_tag {
    type  = "STRINGEQUALS"
    key   = "Backup"
    value = var.name
  }
}

# Báo khi job sao lưu/khôi phục lỗi.
resource "aws_sns_topic" "backup" {
  name              = "${var.name}-backup-events"
  kms_master_key_id = "alias/aws/sns"
  tags              = var.tags
}

data "aws_iam_policy_document" "sns" {
  statement {
    actions   = ["sns:Publish"]
    resources = [aws_sns_topic.backup.arn]
    principals {
      type        = "Service"
      identifiers = ["backup.amazonaws.com"]
    }
    condition {
      test     = "StringEquals"
      variable = "aws:SourceAccount"
      values   = [data.aws_caller_identity.this.account_id]
    }
  }
}

resource "aws_sns_topic_policy" "backup" {
  arn    = aws_sns_topic.backup.arn
  policy = data.aws_iam_policy_document.sns.json
}

resource "aws_backup_vault_notifications" "this" {
  backup_vault_name   = aws_backup_vault.this.name
  sns_topic_arn       = aws_sns_topic.backup.arn
  backup_vault_events = ["BACKUP_JOB_FAILED", "BACKUP_JOB_EXPIRED", "RESTORE_JOB_COMPLETED", "COPY_JOB_FAILED"]
}
