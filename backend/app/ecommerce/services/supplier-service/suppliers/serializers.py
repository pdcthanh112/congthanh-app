from rest_framework import serializers

from .models import Supplier


class SupplierSerializer(serializers.ModelSerializer):
    ownerId = serializers.CharField(source="owner_id", read_only=True)
    avatarUrl = serializers.URLField(source="avatar_url", required=False, allow_blank=True, max_length=1500)
    backgroundUrl = serializers.URLField(source="background_url", required=False, allow_blank=True, max_length=1500)
    createdAt = serializers.DateTimeField(source="created_at", read_only=True)
    updatedAt = serializers.DateTimeField(source="updated_at", read_only=True)

    class Meta:
        model = Supplier
        fields = [
            "id",
            "ownerId",
            "name",
            "slug",
            "description",
            "avatarUrl",
            "backgroundUrl",
            "email",
            "phone",
            "address",
            "status",
            "createdAt",
            "updatedAt",
            "version",
        ]
        read_only_fields = ["id", "status", "version"]
        extra_kwargs = {"slug": {"required": False}}


class PublicSupplierSerializer(SupplierSerializer):
    """Khách xem gian hàng: ẩn chủ sở hữu và liên hệ riêng."""

    class Meta(SupplierSerializer.Meta):
        fields = ["id", "name", "slug", "description", "avatarUrl", "backgroundUrl", "status", "createdAt"]


class UpdateSerializer(SupplierSerializer):
    # Optimistic lock: client gửi version đã đọc; lệch thì 409 thay vì ghi đè thay đổi của người khác.
    version = serializers.IntegerField(required=False, min_value=0)

    class Meta(SupplierSerializer.Meta):
        read_only_fields = ["id", "status"]


class StatusSerializer(serializers.Serializer):
    status = serializers.ChoiceField(choices=Supplier.Status.choices)
