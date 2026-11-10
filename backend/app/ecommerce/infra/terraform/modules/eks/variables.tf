variable "name" { type = string }
variable "kubernetes_version" {
  type    = string
  default = "1.34"
}
variable "vpc_id" { type = string }
variable "subnet_ids" { type = list(string) }
variable "public_endpoint" {
  type    = bool
  default = false
}
variable "tags" {
  type    = map(string)
  default = {}
}
