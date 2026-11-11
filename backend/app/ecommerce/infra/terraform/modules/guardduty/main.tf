# GuardDuty: phát hiện mối đe doạ từ CloudTrail, VPC Flow Logs, DNS, audit log EKS, đăng nhập RDS, dữ liệu S3,
# hành vi trong node EKS và mã độc trên ổ EBS. Finding mức cao gửi qua EventBridge → SNS (email).
# Bổ sung cho Tetragon (platform/security/tetragon): Tetragon chặn trong cluster, GuardDuty nhìn cả tài khoản AWS.

resource "aws_guardduty_detector" "this" {
  enable                       = true
  finding_publishing_frequency = "FIFTEEN_MINUTES"
  tags                         = var.tags
}

locals {
  features = {
    S3_DATA_EVENTS         = true
    EKS_AUDIT_LOGS         = true
    EBS_MALWARE_PROTECTION = true
    RDS_LOGIN_EVENTS       = true
  }
}

resource "aws_guardduty_detector_feature" "this" {
  for_each    = local.features
  detector_id = aws_guardduty_detector.this.id
  name        = each.key
  status      = each.value ? "ENABLED" : "DISABLED"
}

resource "aws_guardduty_detector_feature" "runtime" {
  detector_id = aws_guardduty_detector.this.id
  name        = "RUNTIME_MONITORING"
  status      = var.runtime_monitoring ? "ENABLED" : "DISABLED"
  additional_configuration {
    name   = "EKS_ADDON_MANAGEMENT"
    status = var.runtime_monitoring ? "ENABLED" : "DISABLED"
  }
}

resource "aws_sns_topic" "alerts" {
  name              = "${var.name}-guardduty"
  kms_master_key_id = "alias/aws/sns"
  tags              = var.tags
}

resource "aws_sns_topic_subscription" "email" {
  for_each  = toset(var.alert_emails)
  topic_arn = aws_sns_topic.alerts.arn
  protocol  = "email"
  endpoint  = each.value
}

resource "aws_cloudwatch_event_rule" "findings" {
  name        = "${var.name}-guardduty-findings"
  description = "GuardDuty finding mức >= ${var.min_severity}"
  event_pattern = jsonencode({
    source        = ["aws.guardduty"]
    "detail-type" = ["GuardDuty Finding"]
    detail        = { severity = [{ numeric = [">=", var.min_severity] }] }
  })
  tags = var.tags
}

resource "aws_cloudwatch_event_target" "sns" {
  rule = aws_cloudwatch_event_rule.findings.name
  arn  = aws_sns_topic.alerts.arn
  input_transformer {
    input_paths = {
      severity = "$.detail.severity"
      type     = "$.detail.type"
      title    = "$.detail.title"
      region   = "$.region"
      id       = "$.detail.id"
    }
    input_template = "\"GuardDuty <severity> <type>: <title> (region <region>, finding <id>)\""
  }
}

data "aws_iam_policy_document" "sns" {
  statement {
    actions   = ["sns:Publish"]
    resources = [aws_sns_topic.alerts.arn]
    principals {
      type        = "Service"
      identifiers = ["events.amazonaws.com"]
    }
    condition {
      test     = "ArnEquals"
      variable = "aws:SourceArn"
      values   = [aws_cloudwatch_event_rule.findings.arn]
    }
  }
}

resource "aws_sns_topic_policy" "alerts" {
  arn    = aws_sns_topic.alerts.arn
  policy = data.aws_iam_policy_document.sns.json
}
