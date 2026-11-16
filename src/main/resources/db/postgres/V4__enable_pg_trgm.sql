-- PostgreSQL only. Needed for fast "contains" search (LIKE '%text%').
-- Requires a role allowed to create extensions (on managed services: rds_superuser / azure_pg_admin / cloudsqlsuperuser).
-- Kept in its own migration: it is transactional, while V5 (CREATE INDEX CONCURRENTLY) is not.
CREATE EXTENSION IF NOT EXISTS pg_trgm;
