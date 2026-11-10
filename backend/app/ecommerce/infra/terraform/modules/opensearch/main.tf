# OpenSearch cho search-service (full-text + kNN). Truy cập qua VPC, fine-grained access control.
resource "aws_security_group" "this" {
  name   = "${var.name}-opensearch"
  vpc_id = var.vpc_id
  ingress {
    from_port       = 443
    to_port         = 443
    protocol        = "tcp"
    security_groups = [var.allowed_security_group_id]
  }
  tags = var.tags
}

resource "aws_opensearch_domain" "this" {
  domain_name    = var.name
  engine_version = "OpenSearch_2.19"

  cluster_config {
    instance_type            = var.instance_type
    instance_count           = var.instance_count
    zone_awareness_enabled   = var.instance_count > 1
    dedicated_master_enabled = var.instance_count > 2
    dynamic "zone_awareness_config" {
      for_each = var.instance_count > 1 ? [1] : []
      content { availability_zone_count = min(var.instance_count, 3) }
    }
  }
  ebs_options {
    ebs_enabled = true
    volume_type = "gp3"
    volume_size = var.volume_size_gb
  }
  vpc_options {
    subnet_ids         = slice(var.subnet_ids, 0, min(var.instance_count, length(var.subnet_ids)))
    security_group_ids = [aws_security_group.this.id]
  }
  encrypt_at_rest { enabled = true }
  node_to_node_encryption { enabled = true }
  domain_endpoint_options {
    enforce_https       = true
    tls_security_policy = "Policy-Min-TLS-1-2-PFS-2023-10"
  }
  advanced_security_options {
    enabled                        = true
    anonymous_auth_enabled         = false
    internal_user_database_enabled = false
    master_user_options { master_user_arn = var.master_role_arn }
  }
  tags = var.tags
}
