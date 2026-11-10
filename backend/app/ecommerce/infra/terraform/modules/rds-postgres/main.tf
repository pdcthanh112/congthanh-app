# Aurora PostgreSQL 17: logical replication cho Debezium, pgvector, IAM auth, mã hoá KMS.
resource "aws_rds_cluster_parameter_group" "this" {
  name   = "${var.name}-pg17"
  family = "aurora-postgresql17"

  parameter {
    name         = "rds.logical_replication"
    value        = "1"
    apply_method = "pending-reboot"
  }
  parameter {
    name  = "shared_preload_libraries"
    value = "pg_stat_statements"
    apply_method = "pending-reboot"
  }
}

module "aurora" {
  source  = "terraform-aws-modules/rds-aurora/aws"
  version = "~> 9.0"

  name           = var.name
  engine         = "aurora-postgresql"
  engine_version = "17.5"
  instance_class = var.instance_class
  instances      = { for i in range(var.instances) : "i${i}" => {} }

  vpc_id               = var.vpc_id
  db_subnet_group_name = var.db_subnet_group
  security_group_rules = {
    eks = { source_security_group_id = var.allowed_security_group_id }
  }

  database_name                       = "ecommerce"
  master_username                     = "app"
  manage_master_user_password         = true # mật khẩu trong Secrets Manager, ESO đọc ra
  iam_database_authentication_enabled = true
  storage_encrypted                   = true
  deletion_protection                 = var.deletion_protection
  backup_retention_period             = var.backup_retention_days
  performance_insights_enabled        = true
  monitoring_interval                 = 30

  db_cluster_parameter_group_name = aws_rds_cluster_parameter_group.this.name
  serverlessv2_scaling_configuration = var.instance_class == "db.serverless" ? {
    min_capacity = var.serverless_min_acu
    max_capacity = var.serverless_max_acu
  } : null

  tags = var.tags
}
