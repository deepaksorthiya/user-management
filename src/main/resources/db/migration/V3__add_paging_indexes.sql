-- Indexes that back the list / keyset endpoints. Portable (H2 + PostgreSQL).
-- Rule applied here: an endpoint may only sort / filter by columns that are indexed.

-- keyset + filter on the active flag:  WHERE active = ? AND id > ? ORDER BY id LIMIT n
CREATE INDEX idx_users_active_id ON users (active, id);

-- sort=createdAt (newest first / oldest first) without sorting millions of rows
CREATE INDEX idx_users_created_at_id ON users (created_at, id);
