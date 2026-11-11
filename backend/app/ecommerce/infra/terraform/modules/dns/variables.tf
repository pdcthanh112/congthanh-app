variable "domain_name" {
  type        = string
  description = "Tên miền gốc của môi trường, ví dụ congthanhapp.com hoặc staging.congthanhapp.com"
}
variable "create_zone" {
  type        = bool
  default     = true
  description = "false = dùng hosted zone đã có (cùng tên) thay vì tạo mới"
}
variable "tags" {
  type    = map(string)
  default = {}
}
