# CloudFront đứng trước toàn bộ API (APISIX) và ảnh sản phẩm:
#   - Khách kết nối TLS tới điểm biên gần nhất; WAF và Shield chặn tấn công ở biên, trước khi vào VPC.
#   - APISIX nằm sau Network Load Balancer NỘI BỘ, CloudFront vào bằng VPC origin: không có đường nào từ
#     Internet đi thẳng tới gateway, nên không ai lách được WAF.
#   - API mặc định không cache; các đường đọc công khai (sản phẩm, tìm kiếm, tồn kho, giá) cache theo
#     Cache-Control service trả về, giảm tải lúc flash sale (cả nghìn người cùng xem một sản phẩm).

locals {
  api_domain   = "${var.api_subdomain}.${var.domain_name}"
  media_domain = "${var.media_subdomain}.${var.domain_name}"
  api_enabled  = var.api_origin_lb_arn != ""
  cloudfront_zone_id = "Z2FDTNDATAQYW2" # hosted zone cố định của mọi distribution CloudFront

  # Managed policies của CloudFront (id cố định).
  policy = {
    caching_disabled        = "4135ea2d-6df8-44a3-9df3-4b5a84be39ad" # Managed-CachingDisabled
    use_origin_cache_header = "83da9c7e-98b4-4e11-a168-04f0df8e2c65" # Managed-UseOriginCacheControlHeaders-QueryStrings
    all_viewer_except_host  = "b689b0a8-53d0-40ab-baf2-68738e2966ac" # Managed-AllViewerExceptHostHeader
    security_headers        = "67f7725c-6f97-4210-82d7-5512b31e9d03" # Managed-SecurityHeadersPolicy
  }
  # Đường đọc công khai được phép cache (service quyết định bao lâu bằng Cache-Control; không có header = không cache).
  cached_paths = [
    "/api/ecommerce/products*",
    "/api/ecommerce/search*",
    "/api/ecommerce/pricing/products*",
    "/api/ecommerce/inventory/levels*",
    "/api/ecommerce/recommendations*",
  ]
}

data "aws_lb" "api" {
  count = local.api_enabled ? 1 : 0
  arn   = var.api_origin_lb_arn
}

resource "aws_cloudfront_vpc_origin" "api" {
  count = local.api_enabled ? 1 : 0
  vpc_origin_endpoint_config {
    name                   = "${var.name}-apisix"
    arn                    = var.api_origin_lb_arn
    http_port              = 80
    https_port             = 443
    origin_protocol_policy = "http-only" # đi trong mạng AWS từ CloudFront tới NLB nội bộ
    origin_ssl_protocols {
      items    = ["TLSv1.2"]
      quantity = 1
    }
  }
  tags = var.tags
}

resource "aws_cloudfront_distribution" "api" {
  count           = local.api_enabled ? 1 : 0
  enabled         = true
  is_ipv6_enabled = true
  http_version    = "http2and3"
  comment         = "${var.name} API (APISIX)"
  aliases         = [local.api_domain]
  price_class     = var.price_class
  web_acl_id      = var.web_acl_arn

  origin {
    origin_id   = "apisix"
    domain_name = data.aws_lb.api[0].dns_name
    vpc_origin_config {
      vpc_origin_id            = aws_cloudfront_vpc_origin.api[0].id
      origin_read_timeout      = 60 # SSE của chatbot và GraphQL subscription giữ kết nối lâu
      origin_keepalive_timeout = 60
    }
  }

  default_cache_behavior {
    target_origin_id           = "apisix"
    viewer_protocol_policy     = "redirect-to-https"
    allowed_methods            = ["GET", "HEAD", "OPTIONS", "PUT", "POST", "PATCH", "DELETE"]
    cached_methods             = ["GET", "HEAD"]
    compress                   = true
    cache_policy_id            = local.policy.caching_disabled
    origin_request_policy_id   = local.policy.all_viewer_except_host
    response_headers_policy_id = local.policy.security_headers
  }

  dynamic "ordered_cache_behavior" {
    for_each = local.cached_paths
    content {
      path_pattern               = ordered_cache_behavior.value
      target_origin_id           = "apisix"
      viewer_protocol_policy     = "redirect-to-https"
      allowed_methods            = ["GET", "HEAD", "OPTIONS", "PUT", "POST", "PATCH", "DELETE"]
      cached_methods             = ["GET", "HEAD"]
      compress                   = true
      cache_policy_id            = local.policy.use_origin_cache_header
      origin_request_policy_id   = local.policy.all_viewer_except_host
      response_headers_policy_id = local.policy.security_headers
    }
  }

  restrictions {
    geo_restriction { restriction_type = "none" }
  }
  viewer_certificate {
    acm_certificate_arn      = var.certificate_arn
    ssl_support_method       = "sni-only"
    minimum_protocol_version = "TLSv1.2_2021"
  }
  tags = var.tags
}

resource "aws_route53_record" "api" {
  for_each = local.api_enabled ? toset(["A", "AAAA"]) : toset([])
  zone_id  = var.zone_id
  name     = local.api_domain
  type     = each.key
  alias {
    name                   = aws_cloudfront_distribution.api[0].domain_name
    zone_id                = local.cloudfront_zone_id
    evaluate_target_health = false
  }
}

resource "aws_route53_record" "media" {
  for_each = toset(["A", "AAAA"])
  zone_id  = var.zone_id
  name     = local.media_domain
  type     = each.key
  alias {
    name                   = var.media_distribution_domain
    zone_id                = local.cloudfront_zone_id
    evaluate_target_health = false
  }
}

# --- Shield Advanced (tuỳ chọn) ---
locals {
  shield_targets = var.shield_advanced ? merge(
    { media = var.media_distribution_arn, zone = "arn:aws:route53:::hostedzone/${var.zone_id}" },
    local.api_enabled ? { api = aws_cloudfront_distribution.api[0].arn } : {}
  ) : {}
}

resource "aws_shield_protection" "this" {
  for_each     = local.shield_targets
  name         = "${var.name}-${each.key}"
  resource_arn = each.value
  tags         = var.tags
}

# Shield Advanced tự sinh luật WAF chặn DDoS tầng 7 khi phát hiện tấn công (chỉ cho distribution).
resource "aws_shield_application_layer_automatic_response" "this" {
  for_each     = { for k, v in local.shield_targets : k => v if k != "zone" }
  resource_arn = each.value
  action       = "BLOCK"
  depends_on   = [aws_shield_protection.this]
}
