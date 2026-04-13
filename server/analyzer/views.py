import hashlib
from datetime import timedelta

from django.conf import settings
from django.http import HttpResponse
from django.utils import timezone
from rest_framework import status
from rest_framework.permissions import AllowAny
from rest_framework.response import Response
from rest_framework.views import APIView

from .models import AnalysisTask
from .serializers import AnalysisTaskSerializer, AnalysisUploadSerializer
from .tasks import process_pdf


class MaterialAnalyzerView(APIView):
    """
    POST /api/v1/material-analyzer — Upload PDF, start extraction.
    """

    def post(self, request):
        serializer = AnalysisUploadSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)

        uploaded_file = serializer.validated_data["file"]
        client_hash = serializer.validated_data["sha256"]

        # Read file and compute server-side SHA256
        file_bytes = uploaded_file.read()
        server_hash = hashlib.sha256(file_bytes).hexdigest()

        if client_hash != server_hash:
            return Response(
                {"detail": "SHA256 해시 불일치"},
                status=status.HTTP_400_BAD_REQUEST,
            )

        # Check cache: existing completed task with same hash
        cached = (
            AnalysisTask.objects.filter(
                sha256=server_hash,
                status=AnalysisTask.Status.COMPLETED,
                expires_at__gt=timezone.now(),
            )
            .first()
        )
        if cached:
            return Response(
                AnalysisTaskSerializer(cached).data,
                status=status.HTTP_200_OK,
            )

        # Check if already processing
        processing = (
            AnalysisTask.objects.filter(
                sha256=server_hash,
                status__in=[
                    AnalysisTask.Status.QUEUED,
                    AnalysisTask.Status.PROCESSING,
                ],
            )
            .first()
        )
        if processing:
            return Response(
                AnalysisTaskSerializer(processing).data,
                status=status.HTTP_202_ACCEPTED,
            )

        # Save file to disk
        expiry = settings.ANALYZER_RESULT_EXPIRY_HOURS
        expires_at = timezone.now() + timedelta(hours=expiry)

        task = AnalysisTask.objects.create(
            user=request.user,
            sha256=server_hash,
            original_filename=uploaded_file.name,
            expires_at=expires_at,
        )

        upload_dir = settings.MEDIA_ROOT / "uploads"
        upload_dir.mkdir(parents=True, exist_ok=True)
        file_path = upload_dir / f"{task.id}.pdf"
        file_path.write_bytes(file_bytes)

        # Dispatch Celery task
        process_pdf.delay(str(task.id), str(file_path))

        return Response(
            AnalysisTaskSerializer(task).data,
            status=status.HTTP_202_ACCEPTED,
        )


class MaterialAnalyzerDetailView(APIView):
    """
    GET /api/v1/material-analyzer/{id} — Check status.
    GET /api/v1/material-analyzer/{id}.md — Get Markdown result.
    GET /api/v1/material-analyzer/{id}.json — Get JSON result.
    """

    def get(self, request, task_id, fmt=None):
        try:
            task = AnalysisTask.objects.get(id=task_id)
        except AnalysisTask.DoesNotExist:
            return Response(
                {"detail": "결과를 찾을 수 없습니다"},
                status=status.HTTP_404_NOT_FOUND,
            )

        # Check expiry
        if task.expires_at < timezone.now():
            task.delete()
            return Response(
                {"detail": "결과가 만료되었습니다"},
                status=status.HTTP_404_NOT_FOUND,
            )

        if task.status == AnalysisTask.Status.FAILED:
            return Response(
                {
                    "status": "failed",
                    "error": task.error_message,
                },
                status=status.HTTP_500_INTERNAL_SERVER_ERROR,
            )

        if task.status in (
            AnalysisTask.Status.QUEUED,
            AnalysisTask.Status.PROCESSING,
        ):
            return Response(
                AnalysisTaskSerializer(task).data,
                status=status.HTTP_202_ACCEPTED,
            )

        # Completed — return result in requested format
        if fmt == "md":
            return HttpResponse(
                task.result_md,
                content_type="text/markdown; charset=utf-8",
            )
        if fmt == "json":
            return Response(task.result_json)

        return Response(
            AnalysisTaskSerializer(task).data,
            status=status.HTTP_200_OK,
        )


class HealthView(APIView):
    permission_classes = (AllowAny,)

    def get(self, request):
        import shutil

        mineru_available = shutil.which("mineru") is not None
        return Response({
            "status": "ok",
            "mineru": mineru_available,
        })
