output "cluster_name" { value = module.eks.cluster_name }
output "db_writer" { value = module.postgres.writer_endpoint }
output "valkey" { value = module.valkey.primary_endpoint }
output "kafka_bootstrap" { value = module.kafka.bootstrap_brokers_iam }
output "opensearch" { value = module.search.endpoint }
output "media_cdn" { value = module.storage.media_cdn_domain }
