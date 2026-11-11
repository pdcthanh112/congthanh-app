output "project_id" { value = mongodbatlas_project.this.id }
output "cluster_name" { value = mongodbatlas_advanced_cluster.this.name }

# Chuỗi kết nối qua PrivateLink (mongodb+srv://...), chưa có user/database. Ghép thành mongodb_uri trong Vault:
#   mongodb+srv://<user>:<password>@<host>/<database>?retryWrites=true&w=majority
output "private_srv_connection_string" {
  value = try(mongodbatlas_advanced_cluster.this.connection_strings.private_endpoint[0].srv_connection_string, null)
}

output "user_passwords" {
  value     = { for u, p in random_password.user : u => p.result }
  sensitive = true
}
