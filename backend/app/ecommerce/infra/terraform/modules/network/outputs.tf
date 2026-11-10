output "vpc_id" { value = module.vpc.vpc_id }
output "vpc_cidr" { value = module.vpc.vpc_cidr_block }
output "private_subnets" { value = module.vpc.private_subnets }
output "database_subnets" { value = module.vpc.database_subnets }
output "database_subnet_group" { value = module.vpc.database_subnet_group_name }
