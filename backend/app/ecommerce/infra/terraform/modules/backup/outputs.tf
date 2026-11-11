output "vault_arn" { value = aws_backup_vault.this.arn }
output "plan_id" { value = aws_backup_plan.this.id }
# Đăng ký email/Slack vào topic này để nhận báo lỗi sao lưu.
output "notifications_topic_arn" { value = aws_sns_topic.backup.arn }
