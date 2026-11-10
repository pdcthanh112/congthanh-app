# Tests ngoài service

| Thư mục | Công cụ | Khi nào chạy |
|---|---|---|
| `load/k6` | k6 | trước đợt sale lớn, hằng tuần trên staging |
| `chaos` | Chaos Mesh | game day hằng tháng trên staging |
| `contract` | buf breaking, Spectral, oasdiff | mỗi PR (CI) |
| `e2e` | Hurl | sau mỗi lần deploy (ArgoCD PostSync hook) |

Unit/integration test của từng service nằm trong thư mục service (`go test ./...`, `./gradlew test`).
