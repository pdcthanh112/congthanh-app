variable "name" { type = string }
variable "cidr" {
  type    = string
  default = "10.0.0.0/16"
}
variable "azs" { type = list(string) }
variable "single_nat_gateway" {
  type        = bool
  default     = false
  description = "true cho dev để tiết kiệm chi phí"
}
variable "tags" {
  type    = map(string)
  default = {}
}
