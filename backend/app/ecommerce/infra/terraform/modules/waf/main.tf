# AWS WAF cho CloudFront (scope CLOUDFRONT nên module phải được gọi với provider ở us-east-1).
# Thứ tự luật: chặn theo danh tiếng IP → giới hạn tốc độ → bộ luật OWASP/khai thác → bot.
# Shield Standard (chống DDoS tầng 3/4) tự bật miễn phí cho CloudFront và Route 53; Shield Advanced ở module edge.

locals {
  hot_paths = ["/api/ecommerce/flash-sales/", "/api/ecommerce/checkout/"]
  managed = merge(
    {
      ip-reputation  = { priority = 10, group = "AWSManagedRulesAmazonIpReputationList", count_only = [] }
      known-bad      = { priority = 40, group = "AWSManagedRulesKnownBadInputsRuleSet", count_only = [] }
      sqli           = { priority = 50, group = "AWSManagedRulesSQLiRuleSet", count_only = [] }
      # Ảnh tải lên đi thẳng S3 bằng URL ký sẵn, nhưng GraphQL/AI có body lớn: chỉ đếm luật giới hạn kích thước body.
      common         = { priority = 60, group = "AWSManagedRulesCommonRuleSet", count_only = ["SizeRestrictions_BODY"] }
      # VPN/proxy: chỉ đếm để quan sát, nhiều khách thật dùng VPN.
      anonymous-ip   = { priority = 70, group = "AWSManagedRulesAnonymousIpList", count_only = ["AnonymousIPList", "HostingProviderIPList"] }
    },
    var.bot_control ? {
      bot-control = { priority = 80, group = "AWSManagedRulesBotControlRuleSet", count_only = [] }
    } : {}
  )
}

resource "aws_wafv2_web_acl" "this" {
  name  = var.name
  scope = "CLOUDFRONT"

  default_action {
    allow {}
  }

  dynamic "rule" {
    for_each = length(var.blocked_countries) > 0 ? [1] : []
    content {
      name     = "geo-block"
      priority = 5
      action {
        block {}
      }
      statement {
        geo_match_statement { country_codes = var.blocked_countries }
      }
      visibility_config {
        cloudwatch_metrics_enabled = true
        metric_name                = "${var.name}-geo-block"
        sampled_requests_enabled   = true
      }
    }
  }

  rule {
    name     = "rate-limit-per-ip"
    priority = 20
    action {
      block {
        custom_response { response_code = 429 }
      }
    }
    statement {
      rate_based_statement {
        limit                 = var.rate_limit_per_ip
        evaluation_window_sec = 300
        aggregate_key_type    = "IP"
      }
    }
    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "${var.name}-rate-limit"
      sampled_requests_enabled   = true
    }
  }

  # Flash sale và đặt đơn: chặt hơn, đếm riêng trên các đường dẫn nóng.
  rule {
    name     = "rate-limit-hot-paths"
    priority = 30
    action {
      block {
        custom_response { response_code = 429 }
      }
    }
    statement {
      rate_based_statement {
        limit                 = var.hot_path_rate_limit_per_ip
        evaluation_window_sec = 300
        aggregate_key_type    = "IP"
        scope_down_statement {
          or_statement {
            dynamic "statement" {
              for_each = local.hot_paths
              content {
                byte_match_statement {
                  search_string         = statement.value
                  positional_constraint = "STARTS_WITH"
                  field_to_match {
                    uri_path {}
                  }
                  text_transformation {
                    priority = 0
                    type     = "LOWERCASE"
                  }
                }
              }
            }
          }
        }
      }
    }
    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "${var.name}-rate-limit-hot"
      sampled_requests_enabled   = true
    }
  }

  dynamic "rule" {
    for_each = local.managed
    content {
      name     = rule.key
      priority = rule.value.priority
      override_action {
        none {}
      }
      statement {
        managed_rule_group_statement {
          vendor_name = "AWS"
          name        = rule.value.group
          dynamic "rule_action_override" {
            for_each = rule.value.count_only
            content {
              name = rule_action_override.value
              action_to_use {
                count {}
              }
            }
          }
          dynamic "managed_rule_group_configs" {
            for_each = rule.value.group == "AWSManagedRulesBotControlRuleSet" ? [1] : []
            content {
              aws_managed_rules_bot_control_rule_set { inspection_level = "COMMON" }
            }
          }
        }
      }
      visibility_config {
        cloudwatch_metrics_enabled = true
        metric_name                = "${var.name}-${rule.key}"
        sampled_requests_enabled   = true
      }
    }
  }

  visibility_config {
    cloudwatch_metrics_enabled = true
    metric_name                = var.name
    sampled_requests_enabled   = true
  }
  tags = var.tags
}

# Log WAF: tên log group bắt buộc bắt đầu bằng aws-waf-logs-. Chỉ giữ request bị chặn/đếm để giảm chi phí.
resource "aws_cloudwatch_log_group" "this" {
  name              = "aws-waf-logs-${var.name}"
  retention_in_days = var.log_retention_days
  tags              = var.tags
}

resource "aws_wafv2_web_acl_logging_configuration" "this" {
  resource_arn            = aws_wafv2_web_acl.this.arn
  log_destination_configs = [aws_cloudwatch_log_group.this.arn]
  redacted_fields {
    single_header { name = "authorization" }
  }
  redacted_fields {
    single_header { name = "cookie" }
  }
  logging_filter {
    default_behavior = "DROP"
    filter {
      behavior    = "KEEP"
      requirement = "MEETS_ANY"
      condition {
        action_condition { action = "BLOCK" }
      }
      condition {
        action_condition { action = "COUNT" }
      }
    }
  }
}
