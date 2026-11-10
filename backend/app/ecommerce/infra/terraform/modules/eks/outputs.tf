output "cluster_name" { value = module.eks.cluster_name }
output "cluster_endpoint" { value = module.eks.cluster_endpoint }
output "cluster_ca" { value = module.eks.cluster_certificate_authority_data }
output "node_security_group_id" { value = module.eks.node_security_group_id }
output "karpenter_queue" { value = module.karpenter.queue_name }
output "karpenter_node_role" { value = module.karpenter.node_iam_role_name }
