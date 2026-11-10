variable "name" { type = string }
variable "vpc_id" { type = string }
variable "subnet_ids" { type = list(string) }
variable "allowed_security_group_id" { type = string }
variable "master_role_arn" { type = string }
variable "instance_type" {
  type    = string
  default = "r7g.large.search"
}
variable "instance_count" {
  type    = number
  default = 3
}
variable "volume_size_gb" {
  type    = number
  default = 100
}
variable "tags" {
  type    = map(string)
  default = {}
}
