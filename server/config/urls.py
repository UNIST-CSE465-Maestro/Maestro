from django.contrib import admin
from django.urls import include, path

urlpatterns = [
    path("admin/", admin.site.urls),
    path("api/v1/auth/", include("accounts.urls")),
    path("api/v1/material-analyzer/", include("analyzer.urls")),
    path("api/v1/health", include("analyzer.health_urls")),
]
