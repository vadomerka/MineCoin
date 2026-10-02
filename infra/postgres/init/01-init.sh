#!/bin/sh
set -e

psql -v ON_ERROR_STOP=1 \
     --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
     -v user_pw="$USER_DB_PASSWORD" \
     -v finance_pw="$FINANCE_DB_PASSWORD" <<'EOSQL'
CREATE USER user_svc WITH PASSWORD :'user_pw';
CREATE DATABASE users_db OWNER user_svc;
REVOKE ALL ON DATABASE users_db FROM PUBLIC;

CREATE USER finance_svc WITH PASSWORD :'finance_pw';
CREATE DATABASE finance_db OWNER finance_svc;
REVOKE ALL ON DATABASE finance_db FROM PUBLIC;
EOSQL
