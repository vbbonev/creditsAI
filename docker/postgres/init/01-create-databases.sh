#!/usr/bin/env bash
# Creates one database per consumer, each owned by its own user:
#   credits  -> user credits  (the application)
#   keycloak -> user keycloak (Keycloak)
# Runs only when the Postgres volume is empty (first start).
set -euo pipefail

: "${CREDITS_DB_PASSWORD:?CREDITS_DB_PASSWORD is not set}"
: "${KEYCLOAK_DB_PASSWORD:?KEYCLOAK_DB_PASSWORD is not set}"

psql -v ON_ERROR_STOP=1 \
     --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
     -v credits_pw="$CREDITS_DB_PASSWORD" \
     -v keycloak_pw="$KEYCLOAK_DB_PASSWORD" <<-'EOSQL'
	CREATE USER credits WITH PASSWORD :'credits_pw';
	CREATE DATABASE credits OWNER credits;
	REVOKE ALL ON DATABASE credits FROM PUBLIC;

	CREATE USER keycloak WITH PASSWORD :'keycloak_pw';
	CREATE DATABASE keycloak OWNER keycloak;
	REVOKE ALL ON DATABASE keycloak FROM PUBLIC;
EOSQL
