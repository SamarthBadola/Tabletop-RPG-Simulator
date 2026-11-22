-- PostgreSQL can widen varchar to text without rewriting the table.
SET LOCAL lock_timeout = '2s';
SET LOCAL statement_timeout = '10s';

-- squawk-ignore changing-column-type
ALTER TABLE app_user ALTER COLUMN password_hash TYPE TEXT;
