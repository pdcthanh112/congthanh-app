locals {
  name = "congthanhapp-dev"
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

module "valkey" {
  source                    = "../../modules/elasticache"
  name                      = "${local.name}-valkey"
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
  source                    = "../../modules/opensearch"
  name                      = "ecom-dev"
  vpc_id                    = module.network.vpc_id
  subnet_ids                = module.network.private_subnets
  allowed_security_group_id = module.eks.node_security_group_id
  master_role_arn           = var.opensearch_master_role_arn
  instance_type             = "t3.small.search"
  instance_count            = 1
}

module "storage" {
  source              = "../../modules/s3-cdn"
  prefix              = local.name
  acm_certificate_arn = var.media_certificate_arn
}
