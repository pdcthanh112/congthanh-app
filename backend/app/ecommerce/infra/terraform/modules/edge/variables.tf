variable "name" { type = string }
variable "zone_id" { type = string }
variable "domain_name" { type = string }
variable "certificate_arn" {
  type        = string
  description = "Chứng chỉ ACM ở us-east-1 (module dns, output cloudfront_certificate_arn)"
}
variable "web_acl_arn" { type = string }
variable "api_origin_lb_arn" {
  type        = string
  default     = ""
  description = <<-EOT
    ARN của Network Load Balancer nội bộ mà AWS Load Balancer Controller tạo cho Service APISIX (namespace gateway).
    Load balancer chỉ có sau khi cluster chạy, nên lần apply đầu để trống (chưa tạo distribution API), lấy ARN bằng
      aws elbv2 describe-load-balancers --query "LoadBalancers[?contains(LoadBalancerName,'gateway')].LoadBalancerArn"
    rồi apply lại.
  EOT
}
variable "api_subdomain" {
  type    = string
  default = "api"
}
variable "media_subdomain" {
  type    = string
  default = "media"
}
variable "media_distribution_domain" {
  type        = string
  description = "Domain CloudFront của distribution media (module s3-cdn)"
}
variable "media_distribution_arn" { type = string }
variable "price_class" {
  type    = string
  default = "PriceClass_200"
}
variable "shield_advanced" {
  type        = bool
  default     = false
  description = <<-EOT
    Bật bảo vệ Shield Advanced cho distribution và hosted zone. Tài khoản phải đăng ký Shield Advanced trước
    (3.000 USD/tháng, cam kết 1 năm, đăng ký ở console; Terraform không tự đăng ký vì không huỷ được).
  EOT
}
variable "tags" {
  type    = map(string)
  default = {}
}
