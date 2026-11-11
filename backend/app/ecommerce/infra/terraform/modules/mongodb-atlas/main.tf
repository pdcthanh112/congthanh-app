# MongoDB Atlas (MongoDB vận hành, chạy trên AWS cùng region): read model CQRS của product-service.
# Chỉ vào được qua AWS PrivateLink: project không có IP access list nào nên endpoint công khai không dùng được.

locals {
  atlas_region = upper(replace(var.aws_region, "-", "_")) # ap-southeast-1 → AP_SOUTHEAST_1
}

resource "mongodbatlas_project" "this" {
  name   = var.name
  org_id = var.atlas_org_id
}

resource "mongodbatlas_advanced_cluster" "this" {
  project_id                     = mongodbatlas_project.this.id
  name                           = var.name
  cluster_type                   = "REPLICASET"
  mongo_db_major_version         = var.mongodb_major_version
  backup_enabled                 = var.backup_enabled
  pit_enabled                    = var.point_in_time_recovery
  termination_protection_enabled = var.termination_protection

  replication_specs = [{
    region_configs = [{
      provider_name = "AWS"
      region_name   = local.atlas_region
      priority      = 7
      electable_specs = {
        instance_size = var.instance_size
        node_count    = var.node_count
        disk_size_gb  = var.disk_size_gb
      }
      auto_scaling = {
        compute_enabled            = true
        compute_scale_down_enabled = true
        compute_min_instance_size  = var.min_instance_size
        compute_max_instance_size  = var.max_instance_size
        disk_gb_enabled            = true
      }
    }]
  }]

  tags = var.tags

  lifecycle {
    # autoscaling tự đổi cỡ node; terraform không kéo về instance_size ban đầu
    ignore_changes = [replication_specs[0].region_configs[0].electable_specs.instance_size]
  }
}

# --- PrivateLink ---
resource "mongodbatlas_privatelink_endpoint" "this" {
  project_id    = mongodbatlas_project.this.id
  provider_name = "AWS"
  region        = var.aws_region
}

resource "aws_security_group" "privatelink" {
  name   = "${var.name}-atlas-privatelink"
  vpc_id = var.vpc_id
  ingress {
    description     = "MongoDB qua PrivateLink (Atlas dùng dải cổng riêng cho từng node)"
    from_port       = 1024
    to_port         = 65535
    protocol        = "tcp"
    security_groups = [var.allowed_security_group_id]
  }
  tags = var.tags
}

resource "aws_vpc_endpoint" "atlas" {
  vpc_id             = var.vpc_id
  service_name       = mongodbatlas_privatelink_endpoint.this.endpoint_service_name
  vpc_endpoint_type  = "Interface"
  subnet_ids         = var.subnet_ids
  security_group_ids = [aws_security_group.privatelink.id]
  tags               = merge(var.tags, { Name = "${var.name}-atlas" })
}

resource "mongodbatlas_privatelink_endpoint_service" "this" {
  project_id          = mongodbatlas_project.this.id
  private_link_id     = mongodbatlas_privatelink_endpoint.this.private_link_id
  endpoint_service_id = aws_vpc_endpoint.atlas.id
  provider_name       = "AWS"
}

# --- User cho từng service (SCRAM). Mật khẩu ra output sensitive, copy vào Vault. ---
resource "random_password" "user" {
  for_each = var.databases
  length   = 32
  special  = false
}

resource "mongodbatlas_database_user" "service" {
  for_each           = var.databases
  project_id         = mongodbatlas_project.this.id
  auth_database_name = "admin"
  username           = each.key
  password           = random_password.user[each.key].result
  roles {
    role_name     = "readWrite"
    database_name = each.value
  }
  scopes {
    name = mongodbatlas_advanced_cluster.this.name
    type = "CLUSTER"
  }
}
