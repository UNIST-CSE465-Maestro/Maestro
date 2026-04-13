from rest_framework import serializers

from .models import AnalysisTask


class AnalysisTaskSerializer(serializers.ModelSerializer):
    class Meta:
        model = AnalysisTask
        fields = (
            "id",
            "sha256",
            "original_filename",
            "mode",
            "status",
            "created_at",
            "updated_at",
            "expires_at",
        )
        read_only_fields = fields


class AnalysisUploadSerializer(serializers.Serializer):
    file = serializers.FileField()
    sha256 = serializers.CharField(max_length=64)
    mode = serializers.ChoiceField(
        choices=["standard", "ai"],
        default="standard",
    )
