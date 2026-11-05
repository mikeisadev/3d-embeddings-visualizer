-- Demo database for Embedding Explorer 3D.
-- Same extensions as the L17 init.sql and the same table Spring AI PgVectorStore creates.

CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS public.vector_store (
    id uuid DEFAULT uuid_generate_v4() PRIMARY KEY,
    content text,
    metadata json,
    embedding vector(1536)
);

-- A second table, like the per-model tables of L18 §12 (all-MiniLM-L6-v2 has 384 dimensions).
CREATE TABLE IF NOT EXISTS public.vector_store_minilm (
    id uuid DEFAULT uuid_generate_v4() PRIMARY KEY,
    content text,
    metadata json,
    embedding vector(384)
);
