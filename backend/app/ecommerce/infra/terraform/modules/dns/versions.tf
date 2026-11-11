terraform {
  required_version = ">= 1.10"
  required_providers {
    aws = {
      source                = "hashicorp/aws"
      version               = "~> 6.0"
      configuration_aliases = [aws.us_east_1] # chứng chỉ cho CloudFront phải nằm ở us-east-1
    }
  }
}
