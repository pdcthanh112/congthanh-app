variable "name" { type = string }
variable "vpc_id" { type = string }
variable "db_subnet_group" { type = string }
variable "allowed_security_group_id" { type = string }
variable "instance_class" {
  type    = string
  default = "db.serverless"
}
variable "instances" {
  type    = number
  default = 2
}
variable "serverless_min_acu" {
  type    = number
  default = 0.5
}
variable "serverless_max_acu" {
  type    = number
  default = 16
}
variable "deletion_protection" {
  type    = bool
  default = true
}
variable "backup_retention_days" {
  type    = number
  default = 14
}
variable "tags" {
  type    = map(string)
  default = {}
}
