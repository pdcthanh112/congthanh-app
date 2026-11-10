# EKS + node group hệ thống nhỏ; workload ứng dụng do Karpenter cấp node (spot + on-demand).
module "eks" {
  source  = "terraform-aws-modules/eks/aws"
  version = "~> 21.0"

  name               = var.name
  kubernetes_version = var.kubernetes_version
  vpc_id             = var.vpc_id
  subnet_ids         = var.subnet_ids

  endpoint_public_access                   = var.public_endpoint
  enable_cluster_creator_admin_permissions = true
  enable_irsa                              = false # dùng EKS Pod Identity

  addons = {
    coredns                = {}
    kube-proxy             = {}
    vpc-cni                = { before_compute = true }
    eks-pod-identity-agent = { before_compute = true }
    aws-ebs-csi-driver     = {}
  }

  eks_managed_node_groups = {
    system = {
      instance_types = ["m7g.large"]
      ami_type       = "AL2023_ARM_64_STANDARD"
      min_size       = 2
      max_size       = 4
      desired_size   = 2
      labels         = { "workload" = "system" }
      taints         = { critical = { key = "CriticalAddonsOnly", value = "true", effect = "NO_SCHEDULE" } }
    }
  }

  node_security_group_tags = { "karpenter.sh/discovery" = var.name }
  tags                     = var.tags
}

module "karpenter" {
  source  = "terraform-aws-modules/eks/aws//modules/karpenter"
  version = "~> 21.0"

  cluster_name                    = module.eks.cluster_name
  create_pod_identity_association = true
  node_iam_role_additional_policies = {
    AmazonSSMManagedInstanceCore = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
  }
  tags = var.tags
}
