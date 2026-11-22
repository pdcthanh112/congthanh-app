from django.urls import path
from drf_spectacular.views import SpectacularJSONAPIView, SpectacularSwaggerView

from suppliers import health, urls

# Docs khai trước route `<key>` của suppliers để "docs"/"openapi.json" không bị hiểu là slug gian hàng.
DOCS = "api/ecommerce/suppliers"

urlpatterns = [
    path("health/live", health.live),
    path("health/ready", health.ready),
    path(f"{DOCS}/openapi.json", SpectacularJSONAPIView.as_view(), name="schema"),
    path(f"{DOCS}/docs", SpectacularSwaggerView.as_view(url_name="schema"), name="swagger-ui"),
    *urls.urlpatterns,
]
