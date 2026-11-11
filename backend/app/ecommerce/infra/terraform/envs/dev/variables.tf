variable "region" {
  type    = string
  default = "ap-southeast-1"
}
variable "domain_name" {
  type        = string
  description = "Tên miền của môi trường; module dns tạo hosted zone và chứng chỉ cho nó"
}
variable "api_origin_lb_arn" {
  type        = string
  default     = ""
  description = "ARN NLB nội bộ của APISIX; để trống ở lần apply đầu (xem modules/edge/variables.tf)"
}
variable "elastic_privatelink_service_name" {
  type        = string
  description = "VPC endpoint service của Elastic Cloud cho region này (xem modules/elastic-cloud/variables.tf)"
}
variable "atlas_org_id" {
  type        = string
  description = "Organization ID trên MongoDB Atlas"
}
