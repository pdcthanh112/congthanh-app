from django.db import connection
from django.http import JsonResponse


def live(_request):
    return JsonResponse({"status": "UP"})


def ready(_request):
    try:
        with connection.cursor() as c:
            c.execute("SELECT 1")
    except Exception:
        return JsonResponse({"status": "DOWN"}, status=503)
    return JsonResponse({"status": "UP"})
