locals {
  name = "congthanhapp-test"
  azs  = ["ap-southeast-1a", "ap-southeast-1b", "ap-southeast-1c"]
}

module "network" {
  source             = "../../modules/network"
  name               = local.name
  azs                = local.azs
  single_nat_gateway = true
}

module "eks" {
  source             = "../../modules/eks"
  name               = local.name
  kubernetes_version = "1.34"
  vpc_id             = module.network.vpc_id
  subnet_ids         = module.network.private_subnets
  public_endpoint    = true
}

module "postgres" {
  source                    = "../../modules/rds-postgres"
  name                      = "${local.name}-pg"
  vpc_id                    = module.network.vpc_id
  db_subnet_group           = module.network.database_subnet_group
  allowed_security_group_id = module.eks.node_security_group_id
  instance_class            = "db.serverless"
  instances                 = 1
  deletion_protection       = false
}

module "redis" {
  source                    = "../../modules/elasticache"
  name                      = "${local.name}-redis"
  vpc_id                    = module.network.vpc_id
  subnet_ids                = module.network.database_subnets
  allowed_security_group_id = module.eks.node_security_group_id
  node_type                 = "cache.t4g.small"
  replicas                  = 0
}

module "kafka" {
  source                    = "../../modules/msk"
  name                      = "${local.name}-kafka"
  vpc_id                    = module.network.vpc_id
  subnet_ids                = module.network.private_subnets
  allowed_security_group_id = module.eks.node_security_group_id
  brokers                   = 3
  instance_type             = "kafka.t3.small"
}

module "search" {
  source                    = "../../modules/elastic-cloud"
  name                      = "${local.name}-search"
  aws_region                = var.region
  vpc_id                    = module.network.vpc_id
  subnet_ids                = module.network.private_subnets
  allowed_security_group_id = module.eks.node_security_group_id
  privatelink_service_name  = var.elastic_privatelink_service_name
  hot_size                  = "1g"
  hot_max_size              = "4g"
  zone_count                = 1
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
  bot_control = false
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
  price_class               = "PriceClass_100"
}

# Checkpoint/savepoint của stream-processor (Flink). Checkpoint cũ tự dọn sau 7 ngày, savepoint đặt ở prefix savepoints/.
module "flink_state" {
  source           = "../../modules/workload-bucket"
  name             = "${local.name}-flink-state"
  cluster_name     = module.eks.cluster_name
  namespace        = "streaming"
  service_accounts = ["flink"]
  force_destroy    = true
}

# Backup cluster (Velero): manifest + snapshot EBS của PVC.
module "velero_backup" {
  source           = "../../modules/workload-bucket"
  name             = "${local.name}-velero"
  cluster_name     = module.eks.cluster_name
  namespace        = "velero"
  service_accounts = ["velero"]
  expire_days      = 14
  force_destroy    = true
  extra_actions = [
    "ec2:DescribeVolumes", "ec2:DescribeSnapshots", "ec2:CreateTags", "ec2:CreateVolume",
    "ec2:CreateSnapshot", "ec2:DeleteSnapshot",
  ]
}
