# Postgres 18 + pgvector cho local. Debezium dùng plugin pgoutput có sẵn (không cần wal2json).
FROM pgvector/pgvector:0.8.1-pg18-trixie
COPY init.sql /docker-entrypoint-initdb.d/00-init.sql
CMD ["postgres", "-c", "wal_level=logical", "-c", "max_replication_slots=20", "-c", "max_wal_senders=20", "-c", "shared_preload_libraries=pg_stat_statements"]
