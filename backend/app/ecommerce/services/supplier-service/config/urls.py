from django.urls import path

from suppliers import health, urls

urlpatterns = [
    path("health/live", health.live),
    path("health/ready", health.ready),
    *urls.urlpatterns,
]
