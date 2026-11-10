# Amazon MSK (Kafka 4.x, KRaft) — thay cho Strimzi khi chạy trên AWS. SASL/IAM, mã hoá in-transit.
resource "aws_security_group" "this" {
  name   = "${var.name}-msk"
  vpc_id = var.vpc_id
  ingress {
    from_port       = 9098
    to_port         = 9098
    protocol        = "tcp"
    security_groups = [var.allowed_security_group_id]
  }
  tags = var.tags
}

resource "aws_msk_configuration" "this" {
  name           = var.name
  kafka_versions = [var.kafka_version]
  server_properties = <<-PROPS
    auto.create.topics.enable=false
    default.replication.factor=3
    min.insync.replicas=2
    num.partitions=6
    log.retention.hours=168
  PROPS
}

resource "aws_msk_cluster" "this" {
  cluster_name           = var.name
  kafka_version          = var.kafka_version
  number_of_broker_nodes = var.brokers

  broker_node_group_info {
    instance_type   = var.instance_type
    client_subnets  = var.subnet_ids
    security_groups = [aws_security_group.this.id]
    storage_info {
      ebs_storage_info { volume_size = var.volume_size_gb }
    }
  }

  client_authentication {
    sasl { iam = true }
  }
  encryption_info {
    encryption_in_transit {
      client_broker = "TLS"
      in_cluster    = true
    }
  }
  configuration_info {
    arn      = aws_msk_configuration.this.arn
    revision = aws_msk_configuration.this.latest_revision
  }
  open_monitoring {
    prometheus {
      jmx_exporter { enabled_in_broker = true }
      node_exporter { enabled_in_broker = true }
    }
  }
  tags = var.tags
}
