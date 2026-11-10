from django.urls import path

from . import views

PREFIX = "api/ecommerce/suppliers"
urlpatterns = [
    path(PREFIX, views.collection),
    path(f"{PREFIX}/me", views.mine),
    path(f"{PREFIX}/<str:key>", views.detail),
    path(f"{PREFIX}/<str:key>/status", views.status),
]
