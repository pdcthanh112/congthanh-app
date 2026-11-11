output "zone_id" { value = local.zone_id }
output "zone_arn" {
  value = var.create_zone ? aws_route53_zone.this[0].arn : data.aws_route53_zone.existing[0].arn
}
output "name_servers" {
  value = var.create_zone ? aws_route53_zone.this[0].name_servers : data.aws_route53_zone.existing[0].name_servers
}
output "domain_name" { value = var.domain_name }
output "cloudfront_certificate_arn" { value = aws_acm_certificate_validation.cloudfront.certificate_arn }
output "regional_certificate_arn" { value = aws_acm_certificate_validation.regional.certificate_arn }
