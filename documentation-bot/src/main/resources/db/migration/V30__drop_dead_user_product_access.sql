-- Phase 4.11 — the deprecated unscoped search path (VectorSearchService#search(query, product,
-- version), plus the DocumentChunkRepository queries it alone called) is deleted from the
-- codebase along with the UserProductAccess/ProductAccessService/ProductAccessController stack
-- it backed. Confirmed zero remaining application references before dropping.
DROP TABLE IF EXISTS user_product_access;
