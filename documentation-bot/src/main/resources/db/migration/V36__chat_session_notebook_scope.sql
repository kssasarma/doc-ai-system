-- A chat session can be pinned to a user's personal notebook (document-ingestor owns the
-- `notebooks` table itself — see its V11 migration); when set, retrieval for every message in
-- this session is restricted to that notebook's documents instead of the tenant-wide corpus.
ALTER TABLE chat_sessions ADD COLUMN notebook_id UUID NULL;
