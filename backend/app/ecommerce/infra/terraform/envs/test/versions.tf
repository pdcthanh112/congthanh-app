terraform {
  required_version = ">= 1.10"
  required_providers {
    aws = { source = "hashicorp/aws", version = "~> 6.0" }
  }
  backend "s3" {
    bucket       = "congthanhapp-terraform-state"
    key          = "ecommerce/dev/terraform.tfstate"
    region       = "ap-southeast-1"
    encrypt      = true
    use_lockfile = true # khoá state bằng S3, không cần DynamoDB
  }
}

provider "aws" {
  region = var.region
  default_tags {
    tags = { Project = "congthanhapp-ecommerce", Environment = "dev", ManagedBy = "terraform" }
  }
}

provider "aws" {
  alias  = "us_east_1" # chứng chỉ CloudFront
  region = "us-east-1"
}
