output "vpc_id" {
  value = module.networking.vpc_id
}

output "eks_cluster_endpoint" {
  value = module.kubernetes.cluster_endpoint
}

# Định nghĩa các outputs khác