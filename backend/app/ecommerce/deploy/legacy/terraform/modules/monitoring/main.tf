# Prometheus
resource "kubernetes_deployment" "prometheus" {
  # Cấu hình Prometheus deployment như đã đề cập trước đó
}

resource "kubernetes_config_map" "prometheus_config" {
  # Cấu hình Prometheus ConfigMap như đã đề cập trước đó
}

# Grafana
resource "kubernetes_deployment" "grafana" {
  # Cấu hình Grafana deployment như đã đề cập trước đó
}

resource "kubernetes_service" "grafana" {
  # Cấu hình Grafana service như đã đề cập trước đó
}

# AlertManager
resource "kubernetes_deployment" "alertmanager" {
  # Cấu hình AlertManager deployment như đã đề cập trước đó
}

resource "kubernetes_config_map" "alertmanager_config" {
  # Cấu hình AlertManager ConfigMap như đã đề cập trước đó
}