-- One database per service that owns schema.
--
-- Every service ships its own V1 migration and its own default
-- flyway_schema_history. Pointed at a shared database they collide: the first
-- service to start records version 1, and the next one fails validation with a
-- checksum mismatch rather than applying its own migrations. Separate databases
-- also keep the "no service reaches into another's database" boundary real
-- rather than aspirational.
--
-- Runs only on first initialization of the data volume. After changing this
-- file, recreate the volume: docker compose -f deploy/docker-compose.yml down -v

CREATE DATABASE dfs_auth;
CREATE DATABASE dfs_metadata;
CREATE DATABASE dfs_admin;

GRANT ALL PRIVILEGES ON DATABASE dfs_auth TO dfs;
GRANT ALL PRIVILEGES ON DATABASE dfs_metadata TO dfs;
GRANT ALL PRIVILEGES ON DATABASE dfs_admin TO dfs;
