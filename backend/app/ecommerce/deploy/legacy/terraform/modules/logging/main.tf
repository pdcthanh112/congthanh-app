# Elasticsearch
resource "aws_elasticsearch_domain" "logs" {
  # Cấu hình Elasticsearch như đã đề cập trước đó
}

# Logstash
resource "aws_instance" "logstash" {
  # Cấu hình Logstash EC2 instance như đã đề cập trước đó
}

# Kibana (là một phần của Elasticsearch domain)