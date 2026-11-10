# VPC 3 AZ: public (NLB), private (EKS node), database (RDS/ElastiCache/MSK) subnets.
module "vpc" {
  source  = "terraform-aws-modules/vpc/aws"
  version = "~> 6.0"

  name = var.name
  cidr = var.cidr
  azs  = var.azs

  public_subnets   = [for i, _ in var.azs : cidrsubnet(var.cidr, 8, i)]
  private_subnets  = [for i, _ in var.azs : cidrsubnet(var.cidr, 4, i + 1)]
  database_subnets = [for i, _ in var.azs : cidrsubnet(var.cidr, 8, i + 100)]

  enable_nat_gateway     = true
  single_nat_gateway     = var.single_nat_gateway
  one_nat_gateway_per_az = !var.single_nat_gateway
  enable_dns_hostnames   = true

  create_database_subnet_group = true

  public_subnet_tags  = { "kubernetes.io/role/elb" = 1 }
  private_subnet_tags = { "kubernetes.io/role/internal-elb" = 1, "karpenter.sh/discovery" = var.name }

  tags = var.tags
}
