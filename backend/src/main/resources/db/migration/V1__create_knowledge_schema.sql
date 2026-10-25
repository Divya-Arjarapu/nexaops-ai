CREATE TABLE documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title TEXT NOT NULL,
    original_filename TEXT NOT NULL,
    media_type TEXT NOT NULL,
    source_uri TEXT,
    content_sha256 TEXT,
    file_size_bytes BIGINT,
    status TEXT NOT NULL DEFAULT 'uploaded',
    status_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT documents_title_not_blank CHECK (length(btrim(title)) > 0),
    CONSTRAINT documents_filename_not_blank CHECK (length(btrim(original_filename)) > 0),
    CONSTRAINT documents_media_type_not_blank CHECK (length(btrim(media_type)) > 0),
    CONSTRAINT documents_sha256_format CHECK (
        content_sha256 IS NULL OR content_sha256 ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT documents_file_size_nonnegative CHECK (
        file_size_bytes IS NULL OR file_size_bytes >= 0
    ),
    CONSTRAINT documents_status_valid CHECK (
        status IN ('uploaded', 'processing', 'ready', 'failed')
    )
);

CREATE TABLE document_chunks (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    chunk_index INTEGER NOT NULL,
    content TEXT NOT NULL,
    page_number INTEGER,
    section_title TEXT,
    source_locator TEXT,
    source_start_offset INTEGER,
    source_end_offset INTEGER,
    embedding_model TEXT NOT NULL DEFAULT 'nomic-embed-text',
    embedding public.vector(768),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT document_chunks_document_order_unique UNIQUE (document_id, chunk_index),
    CONSTRAINT document_chunks_index_nonnegative CHECK (chunk_index >= 0),
    CONSTRAINT document_chunks_content_not_blank CHECK (length(btrim(content)) > 0),
    CONSTRAINT document_chunks_page_positive CHECK (page_number IS NULL OR page_number > 0),
    CONSTRAINT document_chunks_offsets_paired CHECK (
        (source_start_offset IS NULL) = (source_end_offset IS NULL)
    ),
    CONSTRAINT document_chunks_offsets_ordered CHECK (
        source_start_offset IS NULL OR source_end_offset > source_start_offset
    ),
    CONSTRAINT document_chunks_embedding_model_not_blank CHECK (
        length(btrim(embedding_model)) > 0
    )
);

CREATE INDEX documents_status_created_at_idx
    ON documents (status, created_at DESC);

CREATE INDEX documents_content_sha256_idx
    ON documents (content_sha256)
    WHERE content_sha256 IS NOT NULL;

CREATE INDEX document_chunks_source_lookup_idx
    ON document_chunks (document_id, page_number, chunk_index);

CREATE INDEX document_chunks_embedding_hnsw_idx
    ON document_chunks USING hnsw (embedding public.vector_cosine_ops)
    WHERE embedding IS NOT NULL;
