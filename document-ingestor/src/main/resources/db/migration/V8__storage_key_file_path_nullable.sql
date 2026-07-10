-- Phase 3 of multi-tenancy overhaul: documents move off local disk onto S3-compatible storage
-- (MinIO). New documents populate storage_key/storage_type (added by V6, never actually used by
-- any code until now) instead of file_path. file_path becomes nullable — it's still populated on
-- historical rows and cleared to NULL on completed ones (matches existing behavior), but no new
-- row will ever require it.
ALTER TABLE documents ALTER COLUMN file_path DROP NOT NULL;
