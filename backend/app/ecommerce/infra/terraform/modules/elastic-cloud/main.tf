# Elasticsearch cho search-service trên Elastic Cloud (Elastic vận hành, chạy trên AWS cùng region).
# search-service đi vào qua AWS PrivateLink: traffic không ra Internet và deployment chỉ nhận kết nối từ VPC
# endpoint này (traffic filter), dù Elastic Cloud vẫn cấp một endpoint công khai.

data "ec_stack" "this" {
  version_regex = var.elastic_version_regex
  region        = var.aws_region
}

# --- PrivateLink ---
resource "aws_security_group" "privatelink" {
  name   = "${var.name}-elastic-privatelink"
  vpc_id = var.vpc_id
  ingress {
    description     = "HTTPS tới Elasticsearch/Kibana"
    from_port       = 443
    to_port         = 443
    protocol        = "tcp"
    security_groups = [var.allowed_security_group_id]
  }
  tags = var.tags
}

resource "aws_vpc_endpoint" "elastic" {
  vpc_id              = var.vpc_id
  service_name        = var.privatelink_service_name
  vpc_endpoint_type   = "Interface"
  subnet_ids          = var.subnet_ids
  security_group_ids  = [aws_security_group.privatelink.id]
  private_dns_enabled = false # Elastic không cấp private DNS: tự tạo zone bên dưới
  tags                = merge(var.tags, { Name = "${var.name}-elastic" })
}

# *.vpce.<region>.aws.elastic-cloud.com → VPC endpoint, chỉ thấy được trong VPC.
resource "aws_route53_zone" "privatelink" {
  name = "vpce.${var.aws_region}.aws.elastic-cloud.com"
  vpc { vpc_id = var.vpc_id }
  tags = var.tags
}

resource "aws_route53_record" "privatelink" {
  zone_id = aws_route53_zone.privatelink.zone_id
  name    = "*.vpce.${var.aws_region}.aws.elastic-cloud.com"
  type    = "CNAME"
  ttl     = 300
  records = [aws_vpc_endpoint.elastic.dns_entry[0].dns_name]
}

resource "ec_deployment_traffic_filter" "privatelink" {
  name               = "${var.name}-privatelink"
  region             = var.aws_region
  type               = "vpce"
  include_by_default = false
  rule {
    source = aws_vpc_endpoint.elastic.id
  }
}

# --- Deployment ---
resource "ec_deployment" "this" {
  name                   = var.name
  alias                  = var.name
  region                 = var.aws_region
  version                = data.ec_stack.this.version
  deployment_template_id = var.deployment_template_id
  traffic_filter         = [ec_deployment_traffic_filter.privatelink.id]

  elasticsearch = {
    hot = {
      size        = var.hot_size
      zone_count  = var.zone_count
      autoscaling = { max_size = var.hot_max_size }
    }
  }

  kibana = {
    size       = var.kibana_size
    zone_count = 1
  }

  tags = var.tags
}
