#!/bin/bash
set -e
set -u

# ==============================================================================
# ShareRide - Automatic Multiple PostgreSQL Database Initializer
# ------------------------------------------------------------------------------
# Script này được Docker PostgreSQL tự động thực thi khi khởi chạy container lần đầu
# thông qua thư mục /docker-entrypoint-initdb.d/
#
# Cơ chế:
# 1. Quét biến POSTGRES_MULTIPLE_DATABASES (dạng cách nhau bởi dấu phẩy)
# 2. Kiểm tra nếu DB chưa tồn tại thì tự động tạo mới (Idempotent)
# 3. Cấp quyền sở hữu và truy cập đầy đủ cho POSTGRES_USER
# ==============================================================================

function create_database() {
    local database=$1
    database=$(echo "$database" | xargs) # trim whitespace
    if [ -z "$database" ]; then
        return
    fi

    echo "[Postgres Init] Checking database: '$database'..."
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
        SELECT 'CREATE DATABASE $database'
        WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = '$database')\gexec
        GRANT ALL PRIVILEGES ON DATABASE $database TO $POSTGRES_USER;
EOSQL
    echo "[Postgres Init] Database '$database' is ready."
}

# Các database cốt lõi mặc định của hệ thống
DEFAULT_DATABASES="shareride_auth,shareride_notification"
TARGET_DATABASES="${POSTGRES_MULTIPLE_DATABASES:-$DEFAULT_DATABASES}"

echo "=============================================================================="
echo " [ShareRide] Initializing Multiple Databases on Single PostgreSQL Instance"
echo "=============================================================================="

for db in $(echo "$TARGET_DATABASES" | tr ',' ' '); do
    create_database "$db"
done

echo "=============================================================================="
echo " [ShareRide] All Databases Initialized Successfully!"
echo "=============================================================================="

