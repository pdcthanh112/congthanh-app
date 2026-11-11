# Spark 4.0 + Iceberg + Kafka, jar tải sẵn lúc build (không phụ thuộc --packages khi chạy).
# Dùng cho cả Spark Connect server (job batch) và job streaming (spark-submit).
FROM spark:4.0.1-python3

ARG ICEBERG=1.12.0
ARG SPARK=4.0.1
ARG KAFKA=3.9.1
ARG MAVEN=https://repo1.maven.org/maven2

USER root
ADD --chown=spark:spark --chmod=644 \
    ${MAVEN}/org/apache/iceberg/iceberg-spark-runtime-4.0_2.13/${ICEBERG}/iceberg-spark-runtime-4.0_2.13-${ICEBERG}.jar \
    ${MAVEN}/org/apache/iceberg/iceberg-aws-bundle/${ICEBERG}/iceberg-aws-bundle-${ICEBERG}.jar \
    ${MAVEN}/org/apache/spark/spark-sql-kafka-0-10_2.13/${SPARK}/spark-sql-kafka-0-10_2.13-${SPARK}.jar \
    ${MAVEN}/org/apache/spark/spark-token-provider-kafka-0-10_2.13/${SPARK}/spark-token-provider-kafka-0-10_2.13-${SPARK}.jar \
    ${MAVEN}/org/apache/kafka/kafka-clients/${KAFKA}/kafka-clients-${KAFKA}.jar \
    ${MAVEN}/org/apache/commons/commons-pool2/2.12.0/commons-pool2-2.12.0.jar \
    /opt/spark/jars/
COPY spark-defaults.conf /opt/spark/conf/spark-defaults.conf
RUN mkdir -p /checkpoints /tmp/spark-events && chown -R spark:spark /checkpoints /tmp/spark-events

USER spark
ENV PYTHONPATH=/opt/lakehouse/jobs
WORKDIR /opt/lakehouse/jobs
