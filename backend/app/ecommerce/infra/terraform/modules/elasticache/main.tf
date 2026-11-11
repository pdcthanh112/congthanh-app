# ElastiCache for Redis OSS (cluster mode tắt để Lua script nhiều key chạy được; dùng hash tag nếu bật).
# AWS chỉ hỗ trợ Redis OSS tới 7.1 (Redis 8 không có trên ElastiCache); muốn Redis 8 managed thì dùng Redis Cloud.
# Đổi engine valkey → redis không làm tại chỗ được: Terraform sẽ tạo lại replication group (mất dữ liệu cache).
resource "aws_elasticache_subnet_group" "this" {
  name       = var.name
  subnet_ids = var.subnet_ids
}

resource "aws_security_group" "this" {
  name   = "${var.name}-redis"
  vpc_id = var.vpc_id
  ingress {
    from_port       = 6379
    to_port         = 6379
    protocol        = "tcp"
    security_groups = [var.allowed_security_group_id]
  }
  tags = var.tags
}

resource "aws_elasticache_replication_group" "this" {
  replication_group_id       = var.name
  description                = "Redis cho cache, rate limit, flash sale"
  engine                     = "redis"
  engine_version             = "7.1"
  node_type                  = var.node_type
  num_cache_clusters         = var.replicas + 1
  automatic_failover_enabled = var.replicas > 0
  multi_az_enabled           = var.replicas > 0
  subnet_group_name          = aws_elasticache_subnet_group.this.name
  security_group_ids         = [aws_security_group.this.id]
  at_rest_encryption_enabled = true
  transit_encryption_enabled = true
  snapshot_retention_limit   = 3
  tags                       = var.tags
}
