terraform {
  required_version = ">= 1.10"
  required_providers {
    aws = { source = "hashicorp/aws", version = "~> 6.0" }
    ec  = { source = "elastic/ec", version = "~> 0.13" }
  }
  backend "s3" {
    bucket       = "congthanhapp-terraform-state"
    key          = "ecommerce/test/terraform.tfstate"
    region       = "ap-southeast-1"
    encrypt      = true
    use_lockfile = true # khoá state bằng S3, không cần DynamoDB
  }
}

provider "aws" {
  region = var.region
  default_tags {
    tags = { Project = "congthanhapp-ecommerce", Environment = "test", ManagedBy = "terraform" }
  }
}

provider "aws" {
  alias  = "us_east_1" # chứng chỉ và WAF của CloudFront phải ở us-east-1; cũng là region nhận bản sao backup
  region = "us-east-1"
  default_tags {
    tags = { Project = "congthanhapp-ecommerce", Environment = "test", ManagedBy = "terraform" }
  }
}

# Elastic Cloud: đặt biến môi trường EC_API_KEY trước khi chạy terraform.
provider "ec" {}
