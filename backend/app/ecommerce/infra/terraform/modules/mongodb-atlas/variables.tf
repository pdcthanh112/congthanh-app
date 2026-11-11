variable "name" { type = string }
variable "atlas_org_id" {
  type        = string
  description = "Organization ID trên MongoDB Atlas (Organization Settings)"
}
variable "aws_region" {
  type        = string
  description = "Region AWS của VPC; cluster Atlas đặt cùng region để đi PrivateLink"
}
variable "instance_size" {
  type        = string
  default     = "M30"
  description = "Cỡ node lúc khởi tạo. PrivateLink cần tối thiểu M10 (M0/Flex không hỗ trợ)"
}
variable "max_instance_size" {
  type        = string
  default     = "M60"
  description = "Autoscaling tăng tối đa tới cỡ này"
}
variable "min_instance_size" {
  type    = string
  default = "M10"
}
variable "node_count" {
  type        = number
  default     = 3
  description = "Số node electable (replica set), trải trên các AZ của region"
}
variable "disk_size_gb" {
  type    = number
  default = 20
}
variable "mongodb_major_version" {
  type    = string
  default = "8.0" # khớp image local mongo:8.0 trong deploy/local/docker-compose.yaml
}
variable "backup_enabled" {
  type    = bool
  default = true
}
variable "point_in_time_recovery" {
  type    = bool
  default = false
}
variable "termination_protection" {
  type    = bool
  default = false
}
variable "databases" {
  type        = map(string)
  description = "user → database được readWrite, mỗi service một user (vd. { product-service = \"ecommerce_product\" })"
}
variable "vpc_id" { type = string }
variable "subnet_ids" { type = list(string) }
variable "allowed_security_group_id" { type = string }
variable "tags" {
  type    = map(string)
  default = {}
}
