# 0009. GitOps + supply chain

- Trạng thái: Accepted

- Image build bằng `ko` (Go) và Jib (Java), không cần Docker daemon, image distroless non-root.
- CI sinh SBOM (Syft), quét lỗ hổng (Trivy), ký image + attestation bằng Cosign keyless (OIDC của GitHub Actions).
- Kyverno chỉ cho chạy image đã ký từ workflow của repo, cấm `:latest`, bắt buộc probe/resources/non-root.
- ArgoCD đồng bộ từ Git (ApplicationSet sinh app cho mỗi service × môi trường); prod chạy canary Argo Rollouts với phân tích tự động.
