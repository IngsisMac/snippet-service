-- El contenido vive únicamente en el asset-service (Blob Storage). Postgres guarda metadata.
ALTER TABLE snippets DROP COLUMN IF EXISTS content;
