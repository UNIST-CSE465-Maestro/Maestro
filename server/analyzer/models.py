import uuid

from django.conf import settings
from django.db import models


class AnalysisTask(models.Model):
    class Status(models.TextChoices):
        QUEUED = "queued", "대기중"
        PROCESSING = "processing", "처리중"
        COMPLETED = "completed", "완료"
        FAILED = "failed", "실패"

    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    user = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        related_name="analysis_tasks",
    )
    sha256 = models.CharField(max_length=64, db_index=True)
    original_filename = models.CharField(max_length=255)
    status = models.CharField(
        max_length=20,
        choices=Status.choices,
        default=Status.QUEUED,
    )
    result_md = models.TextField(blank=True, default="")
    result_json = models.JSONField(blank=True, default=dict)
    error_message = models.TextField(blank=True, default="")
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)
    expires_at = models.DateTimeField()

    class Meta:
        ordering = ["-created_at"]

    def __str__(self):
        return f"{self.original_filename} ({self.status})"
