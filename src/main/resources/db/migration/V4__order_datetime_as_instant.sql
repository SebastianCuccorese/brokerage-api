-- Order times become instants. The provided orders were stored as Buenos Aires local time
-- without a zone, so that is how the existing values are read, once.
ALTER TABLE orders ALTER COLUMN datetime TYPE timestamptz USING datetime AT TIME ZONE 'America/Argentina/Buenos_Aires';
