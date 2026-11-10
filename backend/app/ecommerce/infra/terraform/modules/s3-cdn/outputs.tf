output "bucket_names" { value = { for k, b in aws_s3_bucket.this : k => b.id } }
output "media_cdn_domain" { value = aws_cloudfront_distribution.media.domain_name }
