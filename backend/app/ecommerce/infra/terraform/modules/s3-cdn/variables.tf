variable "prefix" { type = string }
variable "buckets" {
  type        = list(string)
  default     = ["media", "lake", "pg-backup", "loki", "tempo", "mimir"]
  description = "Phải có \"media\"."
}
variable "media_aliases" {
  type    = list(string)
  default = []
}
variable "acm_certificate_arn" {
  type        = string
  description = "Chứng chỉ ACM ở us-east-1 cho CloudFront"
}
variable "web_acl_arn" {
  type        = string
  default     = null
  description = "WAF (scope CLOUDFRONT) gắn vào distribution media; null = không gắn"
}
variable "tags" {
  type    = map(string)
  default = {}
}
