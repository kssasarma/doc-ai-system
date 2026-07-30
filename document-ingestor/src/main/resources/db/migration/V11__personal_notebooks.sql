-- Personal document libraries ("notebooks") — lets any authenticated user upload their own
-- documents and later chat scoped to just that set, independent of the tenant-wide admin corpus.
CREATE TABLE notebooks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    owner_id UUID NOT NULL,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_notebooks_tenant_owner ON notebooks (tenant_id, owner_id);

ALTER TABLE documents ADD COLUMN owner_id UUID NULL;
ALTER TABLE documents ADD COLUMN notebook_id UUID NULL REFERENCES notebooks (id) ON DELETE CASCADE;

CREATE INDEX idx_documents_notebook ON documents (notebook_id);
