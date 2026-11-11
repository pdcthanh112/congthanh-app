variable "name" { type = string }
variable "aws_region" {
  type        = string
  description = "Region AWS của VPC; deployment Elastic Cloud đặt cùng region để đi PrivateLink"
}
variable "elastic_version_regex" {
  type        = string
  default     = "9.5.?"
  description = "Phiên bản Elasticsearch (khớp với image local trong deploy/local/docker-compose.yaml)"
}
variable "deployment_template_id" {
  type        = string
  default     = "aws-general-purpose"
  description = "aws-general-purpose | aws-storage-optimized | aws-cpu-optimized | aws-vector-search-optimized"
}
variable "hot_size" {
  type        = string
  default     = "4g"
  description = "RAM mỗi node tầng hot lúc khởi tạo; autoscaling tự tăng tới hot_max_size"
}
variable "hot_max_size" {
  type    = string
  default = "60g"
}
variable "zone_count" {
  type    = number
  default = 3
}
variable "kibana_size" {
  type    = string
  default = "1g"
}
variable "vpc_id" { type = string }
variable "subnet_ids" { type = list(string) }
variable "allowed_security_group_id" { type = string }
variable "privatelink_service_name" {
  type        = string
  description = <<-EOT
    Tên VPC endpoint service của Elastic Cloud cho region này (dạng com.amazonaws.vpce.<region>.vpce-svc-...).
    Danh sách chính thức: https://www.elastic.co/docs/deploy-manage/security/aws-privatelink-traffic-filters
  EOT
}
variable "tags" {
  type    = map(string)
  default = {}
}
