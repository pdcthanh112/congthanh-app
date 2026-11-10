output "writer_endpoint" { value = module.aurora.cluster_endpoint }
output "reader_endpoint" { value = module.aurora.cluster_reader_endpoint }
output "master_secret_arn" { value = module.aurora.cluster_master_user_secret[0].secret_arn }
