# Hạ tầng AWS (Terraform)

```
modules/   network · eks (+Karpenter, Pod Identity) · rds-postgres (Aurora PG17, logical replication)
           elasticache (Valkey) · msk (Kafka KRaft, IAM auth) · opensearch · s3-cdn (S3 + CloudFront)
envs/      dev (rẻ: 1 NAT, serverless, 1 node) · prod (multi-AZ, deletion protection)
```

```bash
cd infra/terraform/envs/dev
terraform init && terraform plan
```

Sau khi có cluster: cài ArgoCD rồi `kubectl apply -f deploy/argocd/bootstrap.yaml`, mọi thứ còn lại do GitOps lo.
Trên AWS, Kafka/Postgres/Valkey dùng dịch vụ quản lý ở đây; manifest Strimzi/CNPG trong `platform/` dành cho cluster không phải AWS.
Code Terraform cũ (skeleton) giữ ở `deploy/legacy/terraform`.
