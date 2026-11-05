-- SYNTHETIC demo data: no real PDF is involved. Every chunk says so in its content and has
-- "demo": true in its metadata.
--
-- Vectors are deterministic: every "random" number comes from md5(), so the data is the same
-- on every machine and PostgreSQL version. Each vector is
--     0.7 * shared + 1.0 * topic + 0.35 * reading_position * drift + 1.0 * noise
-- normalized to length 1 like OpenAI embeddings: chunks of the same topic have a cosine
-- similarity around 0.5, different topics around 0.2, and the first three principal
-- components keep only part of the variance, as with real text embeddings.

-- A pseudo-random number in [-1, 1) derived from a text key.
CREATE FUNCTION pg_temp.demo_uniform(key text) RETURNS double precision
    LANGUAGE sql IMMUTABLE
    AS $$ SELECT ('x' || substr(md5(key), 1, 8))::bit(32)::bigint / 2147483648.0 - 1.0 $$;

CREATE TABLE pg_temp.demo_topic (
    topic int PRIMARY KEY,
    title text NOT NULL,
    department text NOT NULL,
    keywords text NOT NULL,
    sentences text[] NOT NULL
);

INSERT INTO pg_temp.demo_topic VALUES
    (0, 'Ferie e permessi', 'HR', 'ferie, permessi, richiesta, approvazione', ARRAY[
        'Le ferie si richiedono dal portale del personale con almeno due settimane di anticipo.',
        'I permessi brevi vanno concordati con il responsabile e registrati entro la fine del mese.',
        'Le ferie non godute possono essere trasferite all''anno successivo entro il limite previsto.']),
    (1, 'Sicurezza sul lavoro', 'Sicurezza', 'sicurezza, emergenza, evacuazione, DPI', ARRAY[
        'In caso di emergenza seguire la segnaletica verso le uscite e raggiungere il punto di raccolta.',
        'I dispositivi di protezione individuale vanno indossati in tutte le aree di produzione.',
        'Ogni infortunio, anche lieve, deve essere segnalato subito al preposto.']),
    (2, 'Rimborsi spese', 'Amministrazione', 'rimborsi, trasferta, ricevute, spese', ARRAY[
        'Le spese di trasferta sono rimborsate presentando le ricevute entro trenta giorni.',
        'I pasti in trasferta sono rimborsati fino al massimale indicato dalla policy aziendale.',
        'Le richieste di rimborso senza ricevuta vengono respinte dall''amministrazione.']),
    (3, 'Smart working', 'IT', 'smart working, lavoro agile, VPN, orari', ARRAY[
        'Il lavoro agile è consentito fino a due giorni alla settimana previo accordo con il responsabile.',
        'Da remoto ci si collega alla rete aziendale solo tramite VPN.',
        'Durante lo smart working valgono gli stessi orari di reperibilità dell''ufficio.']);

-- Five files: one per topic, plus a FAQ whose chunks touch every topic.
CREATE TABLE pg_temp.demo_file (
    file_no int PRIMARY KEY,
    file_name text NOT NULL,
    fixed_topic int
);

INSERT INTO pg_temp.demo_file VALUES
    (0, 'demo-ferie-e-permessi.pdf', 0),
    (1, 'demo-sicurezza-sul-lavoro.pdf', 1),
    (2, 'demo-rimborsi-spese.pdf', 2),
    (3, 'demo-smart-working.pdf', 3),
    (4, 'demo-faq-generali.pdf', NULL);

-- 60 chunks per file, three per page.
CREATE TABLE pg_temp.demo_chunk AS
SELECT f.file_no,
       f.file_name,
       c.chunk_no,
       c.chunk_no / 3 + 1 AS page_number,
       COALESCE(f.fixed_topic, c.chunk_no % 4) AS topic,
       c.chunk_no / 59.0 AS reading_position
FROM pg_temp.demo_file f
CROSS JOIN generate_series(0, 59) AS c(chunk_no);

-- One vector per chunk for a given number of dimensions; the prefix separates the "models".
CREATE FUNCTION pg_temp.demo_vector(prefix text, dims int, file_no int, chunk_no int, topic int,
                                    reading_position double precision)
    RETURNS vector
    LANGUAGE sql IMMUTABLE
    AS $$
        SELECT l2_normalize(array_agg(
                   0.7 * pg_temp.demo_uniform(prefix || ':shared:' || d)
                 + 1.0 * pg_temp.demo_uniform(prefix || ':topic:' || topic || ':' || d)
                 + 0.35 * (2 * reading_position - 1) * pg_temp.demo_uniform(prefix || ':drift:' || topic || ':' || d)
                 + 1.0 * pg_temp.demo_uniform(prefix || ':noise:' || file_no || ':' || chunk_no || ':' || d)
                 ORDER BY d)::vector)
        FROM generate_series(1, dims) AS d
    $$;

CREATE TABLE pg_temp.demo_row AS
SELECT md5('demo-chunk:' || ch.file_no || ':' || ch.chunk_no)::uuid AS id,
       format('[DEMO – dati sintetici] %s · pagina %s, frammento %s. %s %s Questo testo è generato '
                  || 'automaticamente per la demo di Embedding Explorer 3D e non proviene da un PDF reale.',
              t.title, ch.page_number, ch.chunk_no % 3 + 1,
              t.sentences[ch.chunk_no % 3 + 1], t.sentences[(ch.chunk_no + 1) % 3 + 1]) AS content,
       json_build_object(
           'demo', true,
           'original_filename', ch.file_name,
           'source', md5('demo-file:' || ch.file_no)::uuid || '.pdf',
           'file_name', md5('demo-file:' || ch.file_no)::uuid || '.pdf',
           'page_number', ch.page_number,
           'chunk_index', ch.chunk_no,
           'total_chunks', 60,
           'department', t.department,
           'document_id', md5('demo-document:' || ch.file_no)::uuid,
           'excerpt_keywords', t.keywords) AS metadata,
       ch.file_no, ch.chunk_no, ch.topic, ch.reading_position
FROM pg_temp.demo_chunk ch
JOIN pg_temp.demo_topic t ON t.topic = ch.topic;

INSERT INTO public.vector_store (id, content, metadata, embedding)
SELECT id, content, metadata,
       pg_temp.demo_vector('openai', 1536, file_no, chunk_no, topic, reading_position)
FROM pg_temp.demo_row
ORDER BY id;

INSERT INTO public.vector_store_minilm (id, content, metadata, embedding)
SELECT id, content, metadata,
       pg_temp.demo_vector('minilm', 384, file_no, chunk_no, topic, reading_position)
FROM pg_temp.demo_row
ORDER BY id;

-- The index Spring AI creates (COSINE_DISTANCE + HNSW), with its default name.
CREATE INDEX IF NOT EXISTS spring_ai_vector_index ON public.vector_store USING hnsw (embedding vector_cosine_ops);
CREATE INDEX IF NOT EXISTS vector_store_minilm_index ON public.vector_store_minilm USING hnsw (embedding vector_cosine_ops);

ANALYZE public.vector_store;
ANALYZE public.vector_store_minilm;
