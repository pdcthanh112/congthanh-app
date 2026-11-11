terraform {
  required_version = ">= 1.10"
  required_providers {
    aws = {
      source                = "hashicorp/aws"
      version               = "~> 6.0"
      configuration_aliases = [aws.copy] # region nhận bản sao; trỏ cùng region chính nếu không dùng copy_to_region
    }
  }
}
