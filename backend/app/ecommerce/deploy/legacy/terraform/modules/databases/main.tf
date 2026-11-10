resource "aws_db_instance" "mysql" {
  engine         = "mysql"
  engine_version = "8.0"
  instance_class = var.mysql_instance_class
  # Các cấu hình khác cho MySQL
}

resource "aws_db_instance" "postgresql" {
  engine         = "postgres"
  engine_version = "13.7"
  instance_class = var.postgresql_instance_class
  # Các cấu hình khác cho PostgreSQL
}