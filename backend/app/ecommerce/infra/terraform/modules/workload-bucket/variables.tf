variable "name" {
  type        = string
  description = "Tên bucket (toàn cục), cũng là tiền tố tên IAM role"
}
variable "cluster_name" { type = string }
variable "namespace" { type = string }
variable "service_accounts" {
  type        = list(string)
  description = "ServiceAccount trong namespace được dùng role này"
}
variable "expire_days" {
  type        = number
  default     = 0
  description = "Tự xoá object sau N ngày (0 = giữ mãi)"
}
variable "extra_actions" {
  type        = list(string)
  default     = []
  description = "Quyền AWS thêm ngoài bucket (vd. Velero cần ec2 snapshot)"
}
variable "force_destroy" {
  type    = bool
  default = false
}
variable "tags" {
  type    = map(string)
  default = {}
}
