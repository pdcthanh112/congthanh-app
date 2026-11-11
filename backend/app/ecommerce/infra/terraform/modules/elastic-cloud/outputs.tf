# URL search-service dùng (trong VPC, qua PrivateLink). Lưu vào Vault: ecommerce/search-service elasticsearch_url.
output "elasticsearch_private_url" {
  value = "https://${ec_deployment.this.alias}.es.vpce.${var.aws_region}.aws.elastic-cloud.com"
}
output "kibana_private_url" {
  value = "https://${ec_deployment.this.alias}.kb.vpce.${var.aws_region}.aws.elastic-cloud.com"
}
output "deployment_id" { value = ec_deployment.this.id }
# Chỉ dùng để tạo API key giới hạn cho search-service (xem INSTRUCTION.md), không đưa cho service.
output "elastic_username" { value = ec_deployment.this.elasticsearch_username }
output "elastic_password" {
  value     = ec_deployment.this.elasticsearch_password
  sensitive = true
}
