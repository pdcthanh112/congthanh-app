variable "name" { type = string }
variable "alert_emails" {
  type        = list(string)
  default     = []
  description = "Email nhận cảnh báo GuardDuty mức cao (người nhận phải bấm xác nhận trong email)"
}
variable "min_severity" {
  type        = number
  default     = 7
  description = "Chỉ báo finding từ mức này (GuardDuty: 1-3.9 thấp, 4-6.9 trung bình, 7-8.9 cao, 9-10 nghiêm trọng)"
}
variable "runtime_monitoring" {
  type        = bool
  default     = true
  description = "Theo dõi tiến trình/mạng trong node EKS (GuardDuty tự cài agent dạng EKS add-on)"
}
variable "tags" {
  type    = map(string)
  default = {}
}
