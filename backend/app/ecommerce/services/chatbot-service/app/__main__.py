import uvicorn

from .config import settings

if __name__ == "__main__":
    uvicorn.run(
        "app.main:app",
        host="0.0.0.0",
        port=settings.http_port,
        proxy_headers=True,
        server_header=False,
        log_config=None,
    )
