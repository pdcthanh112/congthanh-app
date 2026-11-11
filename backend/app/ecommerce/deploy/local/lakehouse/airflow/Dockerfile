# Airflow + pyspark-client trong venv riêng: job gửi tới Spark Connect, không đụng tới thư viện của Airflow
# (pyspark-client kéo protobuf/grpcio mới hơn bản Airflow cần).
FROM apache/airflow:3.1.2-python3.12
RUN python -m venv /opt/airflow/spark-client \
 && /opt/airflow/spark-client/bin/pip install --no-cache-dir "pyspark-client==4.0.1"
ENV LAKEHOUSE_PYTHON=/opt/airflow/spark-client/bin/python
