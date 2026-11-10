variable "region" {
  type    = string
  default = "ap-southeast-1"
}
variable "media_certificate_arn" {
  type        = string
  description = "ACM (us-east-1) cho CloudFront media"
}
variable "opensearch_master_role_arn" { type = string }
