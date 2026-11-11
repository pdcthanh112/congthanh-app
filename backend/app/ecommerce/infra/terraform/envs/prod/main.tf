locals {
  name = "congthanhapp-prod"
  azs  = ["ap-southeast-1a", "ap-southeast-1b", "ap-southeast-1c"]
}

module "network" {
  source             = "../../modules/network"
  name               = local.name
  azs                = local.azs
  single_nat_gateway = false
}

module "eks" {
  source             = "../../modules/eks"
  name               = local.name
  kubernetes_version = "1.34"
  vpc_id             = module.network.vpc_id
  subnet_ids         = module.network.private_subnets
  public_endpoint    = false
}

module "postgres" {
  source                    = "../../modules/rds-postgres"
  name                      = "${local.name}-pg"
  vpc_id                    = module.network.vpc_id
  db_subnet_group           = module.network.database_subnet_group
  allowed_security_group_id = module.eks.node_security_group_id
  instance_class            = "db.r7g.xlarge"
  instances                 = 3
  deletion_protection       = true
}

module "redis" {
  source                    = "../../modules/elasticache"
  name                      = "${local.name}-redis"
  vpc_id                    = module.network.vpc_id
  subnet_ids                = module.network.database_subnets
  allowed_security_group_id = module.eks.node_security_group_id
  node_type                 = "cache.r7g.large"
  replicas                  = 2
}

module "kafka" {
  source                    = "../../modules/msk"
  name                      = "${local.name}-kafka"
  vpc_id                    = module.network.vpc_id
  subnet_ids                = module.network.private_subnets
  allowed_security_group_id = module.eks.node_security_group_id
  brokers                   = 3
  instance_type             = "kafka.m7g.large"
}

module "search" {
  source                    = "../../modules/elastic-cloud"
  name                      = "${local.name}-search"
  aws_region                = var.region
  vpc_id                    = module.network.vpc_id
  subnet_ids                = module.network.private_subnets
  allowed_security_group_id = module.eks.node_security_group_id
  privatelink_service_name  = var.elastic_privatelink_service_name
  hot_size                  = "4g"
  hot_max_size              = "60g"
  zone_count                = 3
}

# Read model CQRS của product-service (trang sản phẩm), xem ADR 0020.
module "mongodb" {
  source                    = "../../modules/mongodb-atlas"
  name                      = "${local.name}-mongo"
  atlas_org_id              = var.atlas_org_id
  aws_region                = var.region
  vpc_id                    = module.network.vpc_id
  subnet_ids                = module.network.private_subnets
  allowed_security_group_id = module.eks.node_security_group_id
  instance_size             = "M30"
  min_instance_size         = "M30"
  max_instance_size         = "M60"
  backup_enabled            = true
  point_in_time_recovery    = true
  termination_protection    = true
  databases                 = { product-service = "ecommerce_product" }
}

module "storage" {
  source              = "../../modules/s3-cdn"
  prefix              = local.name
  acm_certificate_arn = module.dns.cloudfront_certificate_arn
  media_aliases       = ["media.${var.domain_name}"]
  web_acl_arn         = module.waf.web_acl_arn
}

# --- Biên: Route 53, ACM, WAF, CloudFront ---
module "dns" {
  source      = "../../modules/dns"
  domain_name = var.domain_name
  providers   = { aws = aws, aws.us_east_1 = aws.us_east_1 }
}

module "waf" {
  source      = "../../modules/waf"
  name        = local.name
  bot_control = true
  providers   = { aws = aws.us_east_1 } # WAF cho CloudFront phải ở us-east-1
}

module "edge" {
  source                    = "../../modules/edge"
  name                      = local.name
  zone_id                   = module.dns.zone_id
  domain_name               = var.domain_name
  certificate_arn           = module.dns.cloudfront_certificate_arn
  web_acl_arn               = module.waf.web_acl_arn
  api_origin_lb_arn         = var.api_origin_lb_arn
  media_distribution_domain = module.storage.media_cdn_domain
  media_distribution_arn    = module.storage.media_cdn_arn
  price_class               = "PriceClass_200"
  shield_advanced           = var.shield_advanced
}

# Checkpoint/savepoint của stream-processor (Flink). Checkpoint cũ tự dọn sau 7 ngày, savepoint đặt ở prefix savepoints/.
module "flink_state" {
  source           = "../../modules/workload-bucket"
  name             = "${local.name}-flink-state"
  cluster_name     = module.eks.cluster_name
  namespace        = "streaming"
  service_accounts = ["flink"]
  force_destroy    = false
}

# Backup cluster (Velero): manifest + snapshot EBS của PVC.
module "velero_backup" {
  source           = "../../modules/workload-bucket"
  name             = "${local.name}-velero"
  cluster_name     = module.eks.cluster_name
  namespace        = "velero"
  service_accounts = ["velero"]
  expire_days      = 90
  force_destroy    = false
  extra_actions = [
    "ec2:DescribeVolumes", "ec2:DescribeSnapshots", "ec2:CreateTags", "ec2:CreateVolume",
    "ec2:CreateSnapshot", "ec2:DeleteSnapshot",
  ]
}

# --- Sao lưu ---
module "backup" {
  source                 = "../../modules/backup"
  name                   = local.name
  resource_arns          = [module.postgres.cluster_arn]
  daily_retention_days   = 35
  monthly_retention_days = 365
  vault_lock             = true
  copy_to_region         = "us-east-1"
  providers              = { aws = aws, aws.copy = aws.us_east_1 }
}

# --- Phát hiện mối đe doạ ---
# GuardDuty bật theo tài khoản + region (mỗi nơi một detector). Các môi trường khác dùng chung tài khoản thì đã
# được detector này bao; nếu tách tài khoản AWS cho từng môi trường thì thêm module vào env đó.
module "guardduty" {
  source       = "../../modules/guardduty"
  name         = local.name
  alert_emails = var.security_alert_emails
}
