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

module "valkey" {
  source                    = "../../modules/elasticache"
  name                      = "${local.name}-valkey"
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
  source                    = "../../modules/opensearch"
  name                      = "ecom-prod"
  vpc_id                    = module.network.vpc_id
  subnet_ids                = module.network.private_subnets
  allowed_security_group_id = module.eks.node_security_group_id
  master_role_arn           = var.opensearch_master_role_arn
  instance_type             = "r7g.large.search"
  instance_count            = 3
}

module "storage" {
  source              = "../../modules/s3-cdn"
  prefix              = local.name
  acm_certificate_arn = var.media_certificate_arn
}
