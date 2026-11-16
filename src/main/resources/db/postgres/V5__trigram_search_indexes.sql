-- PostgreSQL only. GIN trigram indexes on the exact expressions the search query uses
-- (lower(column) LIKE '%text%'). Without them every search is a sequential scan of the users table.
--
-- CONCURRENTLY: does not block writes while the index is built on a big table. Flyway detects it and
-- runs this migration outside a transaction, so keep ONLY such statements in this file.
-- Cost: GIN indexes make writes slower and take disk; drop the ones you do not need.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_username_trgm   ON users USING gin (lower(username)   gin_trgm_ops);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_email_trgm      ON users USING gin (lower(email)      gin_trgm_ops);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_first_name_trgm ON users USING gin (lower(first_name) gin_trgm_ops);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_last_name_trgm  ON users USING gin (lower(last_name)  gin_trgm_ops);
