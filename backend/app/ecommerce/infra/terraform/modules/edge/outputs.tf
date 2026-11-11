output "api_domain" { value = local.api_domain }
output "media_domain" { value = local.media_domain }
output "api_distribution_id" { value = local.api_enabled ? aws_cloudfront_distribution.api[0].id : null }
