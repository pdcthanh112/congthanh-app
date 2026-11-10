terraform {
  backend "s3" {
    bucket = "my-terraform-states"
    key    = "production/terraform.tfstate"
    region = "us-east-1"
  }
}