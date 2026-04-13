import shutil
from pathlib import Path

from django.conf import settings
from django.core.management.base import BaseCommand
from django.utils import timezone

from analyzer.models import AnalysisTask


class Command(BaseCommand):
    help = "Delete expired analysis tasks and their files"

    def handle(self, *args, **options):
        expired = AnalysisTask.objects.filter(
            expires_at__lt=timezone.now()
        )
        count = expired.count()

        for task in expired:
            result_dir = (
                Path(settings.MEDIA_ROOT) / "results" / str(task.id)
            )
            if result_dir.exists():
                shutil.rmtree(result_dir)

        expired.delete()
        self.stdout.write(
            self.style.SUCCESS(f"Deleted {count} expired tasks")
        )
