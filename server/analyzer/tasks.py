import hashlib
import logging
import subprocess
import json
from pathlib import Path

from celery import shared_task
from django.conf import settings

logger = logging.getLogger(__name__)


@shared_task(bind=True, max_retries=2)
def process_pdf(self, task_id: str, file_path: str):
    """Run MinerU on uploaded PDF and store results."""
    from .models import AnalysisTask

    task = AnalysisTask.objects.get(id=task_id)
    task.status = AnalysisTask.Status.PROCESSING
    task.save(update_fields=["status", "updated_at"])

    input_path = Path(file_path)
    output_dir = Path(settings.MEDIA_ROOT) / "results" / str(task_id)
    output_dir.mkdir(parents=True, exist_ok=True)

    try:
        # Verify SHA256
        sha256 = hashlib.sha256(input_path.read_bytes()).hexdigest()
        if sha256 != task.sha256:
            raise ValueError(
                f"SHA256 mismatch: expected {task.sha256}, got {sha256}"
            )

        # Run MinerU CLI
        result = subprocess.run(
            [
                "mineru",
                str(input_path),
                "-o", str(output_dir),
                "-m", "auto",
            ],
            capture_output=True,
            text=True,
            timeout=600,  # 10 min timeout
        )

        if result.returncode != 0:
            raise RuntimeError(
                f"MinerU failed: {result.stderr[:500]}"
            )

        # Find output files
        md_files = list(output_dir.rglob("*.md"))
        json_files = list(output_dir.rglob("*.json"))

        md_content = ""
        if md_files:
            md_content = md_files[0].read_text(encoding="utf-8")

        json_content = {}
        if json_files:
            json_content = json.loads(
                json_files[0].read_text(encoding="utf-8")
            )

        task.result_md = md_content
        task.result_json = json_content
        task.status = AnalysisTask.Status.COMPLETED
        task.save(update_fields=[
            "result_md", "result_json", "status", "updated_at",
        ])

        # Clean up uploaded PDF
        input_path.unlink(missing_ok=True)

    except Exception as exc:
        task.status = AnalysisTask.Status.FAILED
        task.error_message = str(exc)[:1000]
        task.save(update_fields=[
            "status", "error_message", "updated_at",
        ])
        logger.exception("PDF processing failed for task %s", task_id)
