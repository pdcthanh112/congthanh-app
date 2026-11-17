# Image APISIX kèm plugin tuỳ biến trong plugins/. Build từ thư mục services/api-gateway:
#   docker build -t ghcr.io/pdcthanh112/congthanhapp/api-gateway:<tag> .
# Local không cần image này: compose mount thẳng plugins/ vào image apache/apisix.
FROM apache/apisix:3.20.0-debian
COPY --chown=apisix:apisix plugins/ /usr/local/apisix/custom/apisix/plugins/
