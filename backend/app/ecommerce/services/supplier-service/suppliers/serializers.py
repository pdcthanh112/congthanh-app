"""Adapter vào: kiểm input và trình bày aggregate Supplier ra JSON (camelCase); không chứa logic nghiệp vụ."""

from __future__ import annotations

from rest_framework import serializers

from .domain import SupplierStatus


class SupplierSerializer(serializers.Serializer):
    id = serializers.CharField(read_only=True)
    ownerId = serializers.CharField(source="owner_id", read_only=True)
    name = serializers.CharField(max_length=200)
    slug = serializers.SlugField(max_length=220, required=False, allow_blank=True)
    description = serializers.CharField(required=False, allow_blank=True)
    avatarUrl = serializers.URLField(source="avatar_url", required=False, allow_blank=True, max_length=1500)
    backgroundUrl = serializers.URLField(source="background_url", required=False, allow_blank=True, max_length=1500)
    email = serializers.EmailField(max_length=254, required=False, allow_blank=True)
    phone = serializers.CharField(max_length=32, required=False, allow_blank=True)
    address = serializers.CharField(max_length=500, required=False, allow_blank=True)
    status = serializers.CharField(read_only=True)
    createdAt = serializers.DateTimeField(source="created_at", read_only=True)
    updatedAt = serializers.DateTimeField(source="updated_at", read_only=True)
    version = serializers.IntegerField(read_only=True)


class PublicSupplierSerializer(serializers.Serializer):
    """Khách chỉ thấy thông tin hiển thị, không thấy liên hệ và chủ sở hữu."""

    id = serializers.CharField(read_only=True)
    name = serializers.CharField(read_only=True)
    slug = serializers.CharField(read_only=True)
    description = serializers.CharField(read_only=True)
    avatarUrl = serializers.CharField(source="avatar_url", read_only=True)
    backgroundUrl = serializers.CharField(source="background_url", read_only=True)
    status = serializers.CharField(read_only=True)
    createdAt = serializers.DateTimeField(source="created_at", read_only=True)


class UpdateSerializer(SupplierSerializer):
    version = serializers.IntegerField(required=False, min_value=0)


class StatusSerializer(serializers.Serializer):
    status = serializers.ChoiceField(choices=[s.value for s in SupplierStatus])
