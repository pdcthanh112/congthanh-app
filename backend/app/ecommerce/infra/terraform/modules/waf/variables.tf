variable "name" { type = string }
variable "rate_limit_per_ip" {
  type        = number
  default     = 3000
  description = "Số request tối đa mỗi IP trong 5 phút cho toàn site (người dùng thật hiếm khi quá 10 request/giây)"
}
variable "hot_path_rate_limit_per_ip" {
  type        = number
  default     = 600
  description = "Số request tối đa mỗi IP trong 5 phút cho flash sale và đặt đơn (APISIX còn giới hạn theo tài khoản)"
}
variable "bot_control" {
  type        = bool
  default     = true
  description = "AWS Managed Bot Control (mức Common): tính thêm phí theo request"
}
variable "blocked_countries" {
  type        = list(string)
  default     = []
  description = "Mã quốc gia ISO 3166 bị chặn hoàn toàn (để trống = không chặn)"
}
variable "log_retention_days" {
  type    = number
  default = 30
}
variable "tags" {
  type    = map(string)
  default = {}
}
