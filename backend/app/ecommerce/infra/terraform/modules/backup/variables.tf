variable "name" { type = string }
variable "resource_arns" {
  type        = list(string)
  default     = []
  description = "Tài nguyên sao lưu theo ARN (Aurora cluster...). Ngoài ra mọi tài nguyên gắn tag Backup = <name> cũng được sao lưu."
}
variable "daily_retention_days" {
  type    = number
  default = 35
}
variable "monthly_retention_days" {
  type        = number
  default     = 365
  description = "0 = không giữ bản tháng"
}
variable "copy_to_region" {
  type        = string
  default     = ""
  description = "Region nhận bản sao (phòng mất cả region); để trống = không sao chép. Cần provider aws.copy trỏ tới region đó."
}
variable "vault_lock" {
  type        = bool
  default     = false
  description = "Khoá vault (governance mode): không ai, kể cả admin, xoá bản sao lưu sớm hơn min_retention_days mà không có quyền đặc biệt"
}
variable "min_retention_days" {
  type    = number
  default = 7
}
variable "tags" {
  type    = map(string)
  default = {}
}
