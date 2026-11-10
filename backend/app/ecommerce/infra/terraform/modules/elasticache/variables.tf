variable "name" { type = string }
variable "vpc_id" { type = string }
variable "subnet_ids" { type = list(string) }
variable "allowed_security_group_id" { type = string }
variable "node_type" {
  type    = string
  default = "cache.r7g.large"
}
variable "replicas" {
  type    = number
  default = 1
}
variable "tags" {
  type    = map(string)
  default = {}
}
