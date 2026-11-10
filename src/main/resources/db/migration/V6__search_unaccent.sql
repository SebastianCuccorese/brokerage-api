-- Accent-insensitive instrument search backed by trigram indexes.
CREATE EXTENSION IF NOT EXISTS unaccent WITH SCHEMA public;
CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA public;

-- unaccent() is only STABLE because it reads a dictionary, so an index cannot use it directly.
-- Pinning the dictionary by name makes the result fixed, which makes the wrapper safe to declare IMMUTABLE.
CREATE OR REPLACE FUNCTION public.immutable_unaccent(text) RETURNS text
    LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT
    RETURN public.unaccent('public.unaccent'::regdictionary, $1);

CREATE INDEX IF NOT EXISTS instruments_ticker_search_idx
    ON instruments USING gin (public.immutable_unaccent(lower(ticker)) public.gin_trgm_ops);
CREATE INDEX IF NOT EXISTS instruments_name_search_idx
    ON instruments USING gin (public.immutable_unaccent(lower(name)) public.gin_trgm_ops);
