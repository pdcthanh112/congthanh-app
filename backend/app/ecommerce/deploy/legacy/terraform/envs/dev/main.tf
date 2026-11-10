module "networking" {
  source = "../../modules/networking"
  vpc_cidr = var.vpc_cidr
  environment = var.environment
}

module "databases" {
  source = "../../modules/databases"
  instance_class = var.db_instance_class
  environment = var.environment
}