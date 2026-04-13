from django.urls import path

from .views import MaterialAnalyzerDetailView, MaterialAnalyzerView

urlpatterns = [
    path("", MaterialAnalyzerView.as_view(), name="analyzer-upload"),
    path(
        "<uuid:task_id>",
        MaterialAnalyzerDetailView.as_view(),
        name="analyzer-detail",
    ),
    path(
        "<uuid:task_id>.md",
        MaterialAnalyzerDetailView.as_view(),
        {"fmt": "md"},
        name="analyzer-md",
    ),
    path(
        "<uuid:task_id>.json",
        MaterialAnalyzerDetailView.as_view(),
        {"fmt": "json"},
        name="analyzer-json",
    ),
]
