output "cluster_name" { value = module.eks.cluster_name }
output "db_writer" { value = module.postgres.writer_endpoint }
output "redis" { value = module.redis.primary_endpoint }
output "kafka_bootstrap" { value = module.kafka.bootstrap_brokers_iam }
output "elasticsearch_url" { value = module.search.elasticsearch_private_url }
output "kibana_url" { value = module.search.kibana_private_url }
output "name_servers" { value = module.dns.name_servers }
output "api_domain" { value = module.edge.api_domain }
output "media_cdn" { value = module.edge.media_domain }
output "flink_state_bucket" { value = module.flink_state.bucket }
output "velero_bucket" { value = module.velero_backup.bucket }
output "mongodb_private_srv" { value = module.mongodb.private_srv_connection_string }
output "mongodb_user_passwords" {
  value     = module.mongodb.user_passwords
  sensitive = true
}
