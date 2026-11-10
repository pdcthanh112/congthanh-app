-- Database cho các thành phần nền chạy local (mỗi service dùng schema riêng trong "ecommerce").
CREATE DATABASE ecommerce;
CREATE DATABASE keycloak;
CREATE DATABASE temporal;
CREATE DATABASE temporal_visibility;
CREATE DATABASE unleash;
CREATE DATABASE openfga;
CREATE DATABASE registry;
\c ecommerce
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;
