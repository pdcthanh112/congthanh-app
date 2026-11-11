# Route 53 hosted zone của môi trường + chứng chỉ ACM (DNS validation, tự gia hạn):
#   - cloudfront: ở us-east-1, cho mọi distribution CloudFront (api., media., www.)
#   - regional:   ở region chính, cho load balancer/dịch vụ trong VPC nếu cần TLS bằng ACM
# Zone mới tạo: trỏ NS ở nhà đăng ký tên miền (hoặc zone cha) về output name_servers rồi mới validate được.

resource "aws_route53_zone" "this" {
  count = var.create_zone ? 1 : 0
  name  = var.domain_name
  tags  = var.tags
}

data "aws_route53_zone" "existing" {
  count        = var.create_zone ? 0 : 1
  name         = var.domain_name
  private_zone = false
}

locals {
  zone_id = var.create_zone ? aws_route53_zone.this[0].zone_id : data.aws_route53_zone.existing[0].zone_id
  names   = [var.domain_name, "*.${var.domain_name}"]
}

resource "aws_acm_certificate" "cloudfront" {
  provider                  = aws.us_east_1
  domain_name               = var.domain_name
  subject_alternative_names = ["*.${var.domain_name}"]
  validation_method         = "DNS"
  tags                      = var.tags
  lifecycle { create_before_destroy = true }
}

resource "aws_acm_certificate" "regional" {
  domain_name               = var.domain_name
  subject_alternative_names = ["*.${var.domain_name}"]
  validation_method         = "DNS"
  tags                      = var.tags
  lifecycle { create_before_destroy = true }
}

# Hai chứng chỉ cùng tên miền dùng chung bản ghi validation.
resource "aws_route53_record" "validation" {
  for_each = {
    for o in aws_acm_certificate.cloudfront.domain_validation_options : o.domain_name => o
  }
  zone_id         = local.zone_id
  name            = each.value.resource_record_name
  type            = each.value.resource_record_type
  records         = [each.value.resource_record_value]
  ttl             = 300
  allow_overwrite = true
}

resource "aws_acm_certificate_validation" "cloudfront" {
  provider                = aws.us_east_1
  certificate_arn         = aws_acm_certificate.cloudfront.arn
  validation_record_fqdns = [for r in aws_route53_record.validation : r.fqdn]
}

resource "aws_acm_certificate_validation" "regional" {
  certificate_arn         = aws_acm_certificate.regional.arn
  validation_record_fqdns = [for r in aws_route53_record.validation : r.fqdn]
}

# Chỉ CA của Amazon được cấp chứng chỉ cho tên miền này.
resource "aws_route53_record" "caa" {
  zone_id = local.zone_id
  name    = var.domain_name
  type    = "CAA"
  ttl     = 3600
  records = ["0 issue \"amazon.com\"", "0 issuewild \"amazon.com\"", "0 issue \"letsencrypt.org\""]
}
