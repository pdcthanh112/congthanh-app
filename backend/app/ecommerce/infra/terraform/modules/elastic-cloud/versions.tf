terraform {
  required_version = ">= 1.10"
  required_providers {
    aws = { source = "hashicorp/aws", version = "~> 6.0" }
    # Xác thực bằng biến môi trường EC_API_KEY (Elastic Cloud console → Organization → API keys).
    ec = { source = "elastic/ec", version = "~> 0.13" }
  }
}
